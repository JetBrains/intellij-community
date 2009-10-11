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
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;

/**
 * @author max
 */
public class GosubInstruction extends Instruction {
  private final int mySubprogramOffset;

  public GosubInstruction(int subprogramOffset) {
    mySubprogramOffset = subprogramOffset;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    final int returnIndex = getIndex() + 1;
    stateBefore.pushOffset(returnIndex);
    Instruction nextInstruction = runner.getInstruction(mySubprogramOffset);
    return new DfaInstructionState[] {new DfaInstructionState(nextInstruction, stateBefore)};
  }

  public String toString() {
    return "GOSUB: " + mySubprogramOffset;
  }
}
