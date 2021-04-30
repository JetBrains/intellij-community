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

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import org.jetbrains.annotations.NotNull;

public abstract class Instruction {
  private int myIndex = -1;

  public abstract DfaInstructionState[] accept(@NotNull DataFlowRunner runner, @NotNull DfaMemoryState stateBefore);

  public void setIndex(int index) {
    if (myIndex != -1) {
      throw new IllegalStateException("Index is already set");
    }
    myIndex = index;
  }

  public int getIndex() {
    return myIndex;
  }

  /**
   * Copy instruction to another factory. May return itself if the instruction does not depend on the factory.
   *
   * @param factory factory to place the instruction
   * @return copy that bound to the supplied factory, or itself if the instruction does not depend on the factory.
   */
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    return this;
  }

  public final @NotNull DfaInstructionState @NotNull [] nextStates(@NotNull DataFlowRunner runner, @NotNull DfaMemoryState memState) {
    return new DfaInstructionState[]{nextState(runner, memState)};
  }

  public final @NotNull DfaInstructionState nextState(@NotNull DataFlowRunner runner, @NotNull DfaMemoryState memState) {
    return new DfaInstructionState(runner.getInstruction(getIndex() + 1), memState);
  }
}
