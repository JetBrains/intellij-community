// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
    return new FlushVariableInstruction(myVariable.bindToFactory(factory));
  }

  @NotNull
  public DfaVariableValue getVariable() {
    return myVariable;
  }

  @Override
  public List<DfaVariableValue> getWrittenVariables(DfaValueFactory factory) {
    return List.of(myVariable);
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    stateBefore.flushVariable(getVariable());
    return nextStates(interpreter, stateBefore);
  }

  public String toString() {
    return "FLUSH " + myVariable;
  }
}
