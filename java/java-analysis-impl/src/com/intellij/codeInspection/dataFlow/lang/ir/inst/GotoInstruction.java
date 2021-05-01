/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInspection.dataFlow.lang.ir.inst;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
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
  public int[] getSuccessorIndexes() {
    return new int[] {getOffset()};
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowRunner runner, @NotNull DfaMemoryState stateBefore) {
    Instruction nextInstruction = runner.getInstruction(getOffset());
    return new DfaInstructionState[]{new DfaInstructionState(nextInstruction, stateBefore)};
  }

  public String toString() {
    return "GOTO: " + getOffset();
  }

  public void setOffset(final int offset) {
    myOffset = new ControlFlow.FixedOffset(offset);
  }

}
