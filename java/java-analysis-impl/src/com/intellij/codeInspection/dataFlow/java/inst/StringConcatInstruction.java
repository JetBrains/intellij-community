// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.CustomMethodHandlers;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.EvalInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp;
import com.intellij.codeInspection.dataFlow.types.DfIntType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.*;

public class StringConcatInstruction extends EvalInstruction {
  private final @NotNull TypeConstraint myStringType;

  public StringConcatInstruction(@Nullable DfaAnchor anchor, @NotNull TypeConstraint stringType) {
    super(anchor, 2);
    myStringType = stringType;
  }

  @Override
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory,
                                @NotNull DfaMemoryState state,
                                @NotNull DfaValue @NotNull ... arguments) {
    DfaValue left = arguments[0];
    DfaValue right = arguments[1];
    String leftString = state.getDfType(left).getConstantOfType(String.class);
    String rightString = state.getDfType(right).getConstantOfType(String.class);
    if (leftString != null && rightString != null &&
        leftString.length() + rightString.length() <= CustomMethodHandlers.MAX_STRING_CONSTANT_LENGTH_TO_TRACK) {
      return factory.fromDfType(concatenationResult(leftString + rightString, myStringType));
    }
    DfaValue leftLength = SpecialField.STRING_LENGTH.createValue(factory, left);
    DfaValue rightLength = SpecialField.STRING_LENGTH.createValue(factory, right);
    DfType leftRange = state.getDfType(leftLength);
    DfType rightRange = state.getDfType(rightLength);
    DfType resultRange = leftRange instanceof DfIntType ? ((DfIntType)leftRange).eval(rightRange, LongRangeBinOp.PLUS) : INT;
    DfType result = resultRange.isConst(0)
                    ? referenceConstant("", myStringType)
                    : SpecialField.STRING_LENGTH.asDfType(resultRange).meet(myStringType.asDfType());
    return factory.fromDfType(result);
  }

  public String toString() {
    return "STRING_CONCAT";
  }
}
