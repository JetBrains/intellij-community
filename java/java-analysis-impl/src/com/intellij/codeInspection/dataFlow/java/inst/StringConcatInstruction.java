// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.CustomMethodHandlers;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.EvalInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfBooleanType;
import com.intellij.codeInspection.dataFlow.types.DfFloatingPointType;
import com.intellij.codeInspection.dataFlow.types.DfIntType;
import com.intellij.codeInspection.dataFlow.types.DfIntegralType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.NULL;
import static com.intellij.codeInspection.dataFlow.types.DfTypes.concatenationResult;
import static com.intellij.codeInspection.dataFlow.types.DfTypes.intRange;
import static com.intellij.codeInspection.dataFlow.types.DfTypes.intValue;
import static com.intellij.codeInspection.dataFlow.types.DfTypes.referenceConstant;

public class StringConcatInstruction extends EvalInstruction {
  private final @NotNull TypeConstraint myStringType;
  private final boolean myLeftChar;
  private final boolean myRightChar;

  public StringConcatInstruction(@Nullable DfaAnchor anchor, @NotNull TypeConstraint stringType) {
    this(anchor, stringType, false, false);
  }

  /**
   * @param leftChar true if the left operand has the {@code char} type
   * @param rightChar true if the right operand has the {@code char} type
   */
  public StringConcatInstruction(@Nullable DfaAnchor anchor, @NotNull TypeConstraint stringType, boolean leftChar, boolean rightChar) {
    super(anchor, 2);
    myStringType = stringType;
    myLeftChar = leftChar;
    myRightChar = rightChar;
  }

  @Override
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory,
                                @NotNull DfaMemoryState state,
                                @NotNull DfaValue @NotNull ... arguments) {
    DfaValue left = arguments[0];
    DfaValue right = arguments[1];
    String leftString = getString(state, left, myLeftChar);
    String rightString = getString(state, right, myRightChar);
    if (leftString != null && rightString != null &&
        leftString.length() + rightString.length() <= CustomMethodHandlers.MAX_STRING_CONSTANT_LENGTH_TO_TRACK) {
      return factory.fromDfType(concatenationResult(leftString + rightString, myStringType));
    }
    DfIntType leftRange = getLength(factory, state, left, leftString, myLeftChar);
    DfIntType rightRange = getLength(factory, state, right, rightString, myRightChar);
    DfType resultRange = leftRange.eval(rightRange, LongRangeBinOp.PLUS);
    DfType result = resultRange.isConst(0)
                    ? referenceConstant("", myStringType)
                    : SpecialField.STRING_LENGTH.asDfType(resultRange).meet(myStringType.asDfType());
    return factory.fromDfType(result);
  }

  private static @NotNull DfIntType getLength(@NotNull DfaValueFactory factory, @NotNull DfaMemoryState state, @NotNull DfaValue dfaValue, @Nullable String constValue, boolean isChar) {
    if (constValue != null) {
      return intValue(constValue.length());
    }
    if (isChar) {
      return intValue(1);
    }
    DfType lengthType;
    if (dfaValue.getDfType() instanceof DfIntegralType) {
      DfType decimalString = CustomMethodHandlers.numberAsDecimalString(state, dfaValue);
      lengthType = SpecialField.STRING_LENGTH.getFromQualifier(decimalString);
    } else if (dfaValue.getDfType() instanceof DfBooleanType) {
      lengthType = intRange(LongRangeSet.range(4, 5)); // "true" or "false"
    } else if (dfaValue.getDfType() instanceof DfFloatingPointType) {
      lengthType = intRange(LongRangeSet.range(3, 26));
    } else {
      DfaValue lengthValue = SpecialField.STRING_LENGTH.createValue(factory, dfaValue);
      lengthType = state.getDfType(lengthValue);
    }
    if (lengthType instanceof DfIntType intType) return intType;
    return (DfIntType)intRange(LongRangeSet.range(0, Integer.MAX_VALUE));
  }
  
  private static @Nullable String getString(@NotNull DfaMemoryState state, DfaValue value, boolean isChar) {
    DfType dfType = state.getDfType(value);
    if (isChar) {
      // DFA stores a char constant as an int
      return dfType.getConstantOfType(Integer.class) instanceof Integer code ? String.valueOf((char)code.intValue()) : null;
    }
    if (dfType.equals(NULL)) {
      return "null";
    }
    Object constant = dfType.getConstantOfType(Object.class);
    // Do not process float/double constants, as their string representation may depend on JDK version
    return constant instanceof String || constant instanceof Integer || constant instanceof Long || 
           constant instanceof Boolean ? constant.toString() : null;
  }

  @Override
  public String toString() {
    return "STRING_CONCAT";
  }
}
