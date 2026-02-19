// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import org.jetbrains.annotations.NotNull;


public class GotoInstruction extends Instruction {
  private ControlFlow.ControlFlowOffset myOffset;
  private final boolean myShouldWiden;

  public GotoInstruction(ControlFlow.ControlFlowOffset offset) {
    this(offset, true);
  }

  /**
   * @param offset target offset
   * @param shouldWiden if false, widening is not performed at this instruction, even if it's a back-branch.
   *                    Used to mark 'unrolled' loops, which are known to have very few iterations.
   */
  public GotoInstruction(ControlFlow.ControlFlowOffset offset, boolean shouldWiden) {
    myOffset = offset;
    myShouldWiden = shouldWiden;
  }

  public boolean shouldWidenBackBranch() {
    return myShouldWiden;
  }

  public int getOffset() {
    return myOffset.getInstructionOffset();
  }

  @Override
  public int @NotNull [] getSuccessorIndexes() {
    return new int[] {getOffset()};
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    Instruction nextInstruction = interpreter.getInstruction(getOffset());
    return new DfaInstructionState[]{new DfaInstructionState(nextInstruction, stateBefore)};
  }

  @Override
  public String toString() {
    return "GOTO " + getOffset();
  }

  public void setOffset(final int offset) {
    myOffset = new ControlFlow.FixedOffset(offset);
  }

}
