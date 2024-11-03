// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Marks given variables as escaped (usually necessary for captured variables in lambdas/local classes)
 */
public class EscapeInstruction extends Instruction {
  private final @NotNull Set<@NotNull DfaVariableValue> myEscapedVars;

  public EscapeInstruction(@NotNull Set<@NotNull DfaVariableValue> escapedVars) {myEscapedVars = escapedVars;}

  public @NotNull Set<@NotNull DfaVariableValue> getEscapedVars() {
    return myEscapedVars;
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    return new EscapeInstruction(ContainerUtil.map2Set(myEscapedVars, var -> var.bindToFactory(factory)));
  }

  @Override
  public List<VariableDescriptor> getRequiredDescriptors(@NotNull DfaValueFactory factory) {
    return StreamEx.of(myEscapedVars)
      .flatMap(v -> StreamEx.of(v.getDependentVariables()).append(v))
      .map(DfaVariableValue::getDescriptor)
      .distinct()
      .toList();
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
