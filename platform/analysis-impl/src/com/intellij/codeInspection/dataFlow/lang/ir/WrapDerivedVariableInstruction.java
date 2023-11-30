// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DerivedVariableDescriptor;
import com.intellij.codeInspection.dataFlow.value.DfaBinOpValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Create a value that has given {@link DfType} and its {@link DerivedVariableDescriptor} value is taken from the stack.
 * In particular, could be used to box the stack value on JVM (with field = SpecialField.UNBOX).
 */
public class WrapDerivedVariableInstruction extends EvalInstruction {
  private final @NotNull DfType myTargetType;
  private final @NotNull DerivedVariableDescriptor myDerivedVariableDescriptor;

  public WrapDerivedVariableInstruction(@NotNull DfType targetType, @NotNull DerivedVariableDescriptor field) {
    super(null, 1);
    myTargetType = targetType;
    myDerivedVariableDescriptor = field;
  }

  public DerivedVariableDescriptor getDerivedVariableDescriptor() {
    return myDerivedVariableDescriptor;
  }

  @Override
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory,
                                @NotNull DfaMemoryState state,
                                @NotNull DfaValue @NotNull ... arguments) {
    DfaValue value = arguments[0];
    if (value instanceof DfaBinOpValue) {
      value = factory.fromDfType(state.getDfType(value));
    }
    return factory.getWrapperFactory().createWrapper(myTargetType, myDerivedVariableDescriptor, value);
  }

  @Override
  public String toString() {
    return "WRAP [" + myDerivedVariableDescriptor + "] " + myTargetType;
  }
}
