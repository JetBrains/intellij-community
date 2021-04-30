// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir.inst;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

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
  public DfaInstructionState[] accept(@NotNull DataFlowRunner runner, @NotNull DfaMemoryState stateBefore) {
    getEscapedVars().forEach(var -> JavaDfaHelpers.dropLocality(var, stateBefore));
    return nextStates(runner, stateBefore);
  }

  @Override
  public String toString() {
    return "ESCAPE " + myEscapedVars;
  }
}
