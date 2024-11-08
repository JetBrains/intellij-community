// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An instruction that pushes given value to the stack
 */
public class PushInstruction extends EvalInstruction {
  private final @NotNull DfaValue myValue;

  public PushInstruction(@NotNull DfaValue value, DfaAnchor place) {
    super(place, 0);
    myValue = value;
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    return new PushInstruction(getValue().bindToFactory(factory), getDfaAnchor());
  }

  @Override
  public List<VariableDescriptor> getRequiredDescriptors(@NotNull DfaValueFactory factory) {
    return getValue() instanceof DfaVariableValue var ? List.of(var.getDescriptor()) : List.of();
  }

  public @NotNull DfaValue getValue() {
    return myValue;
  }

  @Override
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory, @NotNull DfaMemoryState state, @NotNull DfaValue @NotNull ... arguments) {
    return myValue;
  }

  public String toString() {
    return "PUSH " + myValue;
  }
}
