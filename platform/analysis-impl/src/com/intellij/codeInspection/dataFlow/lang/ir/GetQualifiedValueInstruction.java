// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Instruction to push a field qualified by the value on the stack
 */
public class GetQualifiedValueInstruction extends EvalInstruction {
  private final @NotNull VariableDescriptor myDescriptor;

  public GetQualifiedValueInstruction(@NotNull VariableDescriptor descriptor) {
    this(descriptor, null);
  }

  public GetQualifiedValueInstruction(@NotNull VariableDescriptor descriptor, @Nullable DfaAnchor anchor) {
    super(anchor, 1);
    myDescriptor = descriptor;
  }

  @Override
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory, @NotNull DfaMemoryState state, @NotNull DfaValue @NotNull ... arguments) {
    return myDescriptor.createValue(factory, arguments[0]);
  }

  @Override
  public List<VariableDescriptor> getRequiredDescriptors(@NotNull DfaValueFactory factory) {
    return List.of(myDescriptor);
  }

  @Override
  public String toString() {
    return "GET_FIELD " + myDescriptor;
  }
}
