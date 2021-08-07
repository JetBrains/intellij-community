// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Marks given variables as escaped (usually necessary for captured variables in lambdas/local classes)
 */
public class EscapeInstruction extends Instruction {
  private final Set<DfaVariableValue> myEscapedVars;

  public EscapeInstruction(Set<DfaVariableValue> escapedVars) {myEscapedVars = escapedVars;}

  public Set<DfaVariableValue> getEscapedVars() {
    return myEscapedVars;
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    var instruction = new EscapeInstruction(ContainerUtil.map2Set(myEscapedVars, var -> var.bindToFactory(factory)));
    instruction.setIndex(getIndex());
    return instruction;
  }

  @Override
  public List<DfaVariableValue> getRequiredVariables(DfaValueFactory factory) {
    return List.copyOf(myEscapedVars);
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    getEscapedVars().forEach(var -> JavaDfaHelpers.dropLocality(var, stateBefore));
    return nextStates(interpreter, stateBefore);
  }

  @Override
  public String toString() {
    return "ESCAPE " + myEscapedVars;
  }
}
