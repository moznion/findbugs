/*
 * FindBugs - Find bugs in Java programs
 * Copyright (C) 2003,2004 University of Maryland
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package edu.umd.cs.findbugs.detect;

import edu.umd.cs.findbugs.ResourceCreationPoint;

import edu.umd.cs.findbugs.ba.BasicBlock;
import edu.umd.cs.findbugs.ba.Hierarchy;
import edu.umd.cs.findbugs.ba.Location;
import edu.umd.cs.findbugs.ba.RepositoryLookupFailureCallback;
import edu.umd.cs.findbugs.ba.ResourceValue;
import edu.umd.cs.findbugs.ba.ResourceValueFrame;

import org.apache.bcel.Constants;

import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InvokeInstruction;
import org.apache.bcel.generic.INVOKESPECIAL;
import org.apache.bcel.generic.INVOKEVIRTUAL;
import org.apache.bcel.generic.INVOKEINTERFACE;

/**
 * A Stream object marks the location in the code where a
 * stream is created.  It also is responsible for determining
 * some aspects of how the stream state is tracked
 * by the ResourceValueAnalysis, such as when the stream
 * is opened or closed, and whether implicit exception
 * edges are significant.
 *
 * <p> TODO: change streamClass and streamBase to ObjectType
 *
 * <p> TODO: isStreamOpen() and isStreamClose() should
 * probably be abstract, so we can customize how they work
 * for different kinds of streams
 */
public class Stream extends ResourceCreationPoint {
	private String streamBase;
	private boolean isUninteresting;
	private boolean isOpenOnCreation;
	private InstructionHandle ctorHandle;
	private boolean ignoreImplicitExceptions;
	private String bugType;

	/**
	 * Constructor.
	 * By default, Stream objects are marked as uninteresting.
	 * setInteresting("BUG_TYPE") must be called explicitly to mark
	 * the Stream as interesting.
	 * @param location where the stream is created
	 * @param streamClass type of Stream
	 * @param baseClass highest class in the class hierarchy through which
	 *   stream's close() method could be called
	 */
	public Stream(Location location, String streamClass, String streamBase) {
		super(location, streamClass);
		this.streamBase = streamBase;
		isUninteresting = true;
	}

	/**
	 * Mark this Stream as interesting.
	 * @param bugType the bug type that should be reported if
	 *   the stream is not closed on all paths out of the method
	 */
	public Stream setInteresting(String bugType) {
		this.isUninteresting = false;
		this.bugType = bugType;
		return this;
	}

	/**
	 * Mark whether or not implicit exception edges should be
	 * ignored by ResourceValueAnalysis when determining whether or
	 * not stream is closed on all paths out of method.
	 */
	public Stream setIgnoreImplicitExceptions(boolean enable) {
		ignoreImplicitExceptions = enable;
		return this;
	}

	/**
	 * Mark whether or not Stream is open as soon as it is created,
	 * or whether a later method or constructor must explicitly
	 * open it.
	 */
	public Stream setIsOpenOnCreation(boolean enable) {
		isOpenOnCreation = enable;
		return this;
	}

	public String getStreamBase() { return streamBase; }

	public boolean isUninteresting() { return isUninteresting; }

	public boolean isOpenOnCreation() { return isOpenOnCreation; }

	public void setConstructorHandle(InstructionHandle handle) { this.ctorHandle = handle; }

	public InstructionHandle getConstructorHandle() { return ctorHandle; }

	public boolean ignoreImplicitExceptions() { return ignoreImplicitExceptions; }

	public String getBugType() {
		return bugType;
	}

	public boolean isStreamOpen(BasicBlock basicBlock, InstructionHandle handle,
		ConstantPoolGen cpg, ResourceValueFrame frame) {
		if (isOpenOnCreation)
			return false;

		Instruction ins = handle.getInstruction();
		if (!(ins instanceof INVOKESPECIAL))
			return false;

		// Does this instruction open the stream?
		INVOKESPECIAL inv = (INVOKESPECIAL) ins;

		return frame.isValid()
			&& getInstanceValue(frame, inv, cpg).isInstance()
			&& matchMethod(inv, cpg, this.getResourceClass(), "<init>");
	}

	public boolean isStreamClose(BasicBlock basicBlock, InstructionHandle handle,
		ConstantPoolGen cpg, ResourceValueFrame frame,
		RepositoryLookupFailureCallback lookupFailureCallback) {

		Instruction ins = handle.getInstruction();

		if ((ins instanceof INVOKEVIRTUAL) || (ins instanceof INVOKEINTERFACE)) {
			// Does this instruction close the stream?
			InvokeInstruction inv = (InvokeInstruction) ins;

			if (!frame.isValid() ||
				!getInstanceValue(frame, inv, cpg).isInstance())
				return false;

			// It's a close if the invoked class is any subtype of the stream base class.
			// (Basically, we may not see the exact original stream class,
			// even though it's the same instance.)
			try {
				return inv.getName(cpg).equals("close")
					&& inv.getSignature(cpg).equals("()V")
					&& Hierarchy.isSubtype(inv.getClassName(cpg), streamBase);
			} catch (ClassNotFoundException e) {
				lookupFailureCallback.reportMissingClass(e);
				return false;
			}
		}

		return false;
	}

	private ResourceValue getInstanceValue(ResourceValueFrame frame, InvokeInstruction inv,
		ConstantPoolGen cpg) {
		int numConsumed = inv.consumeStack(cpg);
		if (numConsumed == Constants.UNPREDICTABLE)
			throw new IllegalStateException();
		return frame.getValue(frame.getNumSlots() - numConsumed);
	}

	private boolean matchMethod(InvokeInstruction inv, ConstantPoolGen cpg, String className,
		String methodName) {
		return inv.getClassName(cpg).equals(className)
			&& inv.getName(cpg).equals(methodName);
	}
}

// vim:ts=3
