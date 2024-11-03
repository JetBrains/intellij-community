// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DerivedVariableDescriptor;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Instruction to push a field qualified by the value on the stack
 */
public class UnwrapDerivedVariableInstruction extends EvalInstruction {
  private final @NotNull DerivedVariableDescriptor myDerivedVariableDescriptor;

  public UnwrapDerivedVariableInstruction(@NotNull DerivedVariableDescriptor derivedVariable) {
    super(null, 1);
    myDerivedVariableDescriptor = derivedVariable;
  }

  @Override
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory, @NotNull DfaMemoryState state, @NotNull DfaValue @NotNull ... arguments) {
    return myDerivedVariableDescriptor.createValue(factory, arguments[0]);
  }

  @Override
  public List<VariableDescriptor> getRequiredDescriptors(@NotNull DfaValueFactory factory) {
    return List.of(myDerivedVariableDescriptor);
  }

  @Override
  public String toString() {
    return "UNWRAP " + myDerivedVariableDescriptor;
  }
}
