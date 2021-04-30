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
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import org.jetbrains.annotations.NotNull;

/**
 * Flush single variable
 */
public class FlushVariableInstruction extends Instruction {
  private final @NotNull DfaVariableValue myVariable;

  /**
   * @param variable variable to flush
   */
  public FlushVariableInstruction(@NotNull DfaVariableValue variable) {
    myVariable = variable;
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    var instruction = new FlushVariableInstruction(myVariable.bindToFactory(factory));
    instruction.setIndex(getIndex());
    return instruction;
  }

  @NotNull
  public DfaVariableValue getVariable() {
    return myVariable;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowRunner runner, @NotNull DfaMemoryState stateBefore) {
    stateBefore.flushVariable(getVariable());
    return nextStates(runner, stateBefore);
  }

  public String toString() {
    return "FLUSH " + myVariable;
  }
}
