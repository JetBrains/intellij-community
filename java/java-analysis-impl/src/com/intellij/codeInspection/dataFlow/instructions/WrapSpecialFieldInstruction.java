// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.SpecialField;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaBinOpValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Create a value that has given {@link DfType} and its {@link SpecialField} value is taken from the stack.
 * In particular, could be used to box the stack value (with SpecialField = UNBOX). 
 */
public class WrapSpecialFieldInstruction extends EvalInstruction {
  @NotNull private final DfType myTargetType;
  @NotNull private final SpecialField mySpecialField;

  public WrapSpecialFieldInstruction(@NotNull DfType targetType, @NotNull SpecialField field) {
    super(null, 1);
    myTargetType = targetType;
    mySpecialField = field;
  }

  @Override
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory,
                                @NotNull DfaMemoryState state,
                                @NotNull DfaValue @NotNull ... arguments) {
    DfaValue value = arguments[0];
    if (value instanceof DfaBinOpValue) {
      value = factory.fromDfType(state.getDfType(value));
    }
    return factory.getWrapperFactory().createWrapper(myTargetType, mySpecialField, value);
  }

  @Override
  public String toString() {
    return "WRAP [" + mySpecialField + "] " + myTargetType;
  }
}
