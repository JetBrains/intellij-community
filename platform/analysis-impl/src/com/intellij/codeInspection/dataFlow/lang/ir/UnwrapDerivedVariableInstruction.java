// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DerivedVariableDescriptor;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Instruction to push a field qualified by the value on the stack
 */
public class UnwrapDerivedVariableInstruction extends EvalInstruction {
  @NotNull private final DerivedVariableDescriptor myDerivedVariableDescriptor;

  public UnwrapDerivedVariableInstruction(@NotNull DerivedVariableDescriptor derivedVariable) {
    super(null, 1);
    myDerivedVariableDescriptor = derivedVariable;
  }

  @Override
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory, @NotNull DfaMemoryState state, @NotNull DfaValue @NotNull ... arguments) {
    return myDerivedVariableDescriptor.createValue(factory, arguments[0]);
  }

  @Override
  public String toString() {
    return "UNWRAP " + myDerivedVariableDescriptor;
  }
}
