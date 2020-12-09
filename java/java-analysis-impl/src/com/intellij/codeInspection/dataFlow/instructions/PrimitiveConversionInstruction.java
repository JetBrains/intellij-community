// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.codeInspection.dataFlow.types.DfLongType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaBinOpValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.rangeClamped;

/**
 * A unary instruction that converts a primitive value from the stack to the desired type
 */
public class PrimitiveConversionInstruction extends EvalInstruction {
  @Nullable private final PsiPrimitiveType myTargetType;

  public PrimitiveConversionInstruction(@Nullable PsiPrimitiveType targetType, @Nullable PsiExpression expression) {
    super(expression, 1);
    myTargetType = targetType;
  }

  @Override
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory,
                                @NotNull DfaMemoryState state,
                                @NotNull DfaValue @NotNull ... arguments) {
    DfaValue value = arguments[0];
    PsiPrimitiveType type = myTargetType;
    if (value instanceof DfaBinOpValue) {
      value = ((DfaBinOpValue)value).tryReduceOnCast(state, type);
    }
    if (value instanceof DfaVariableValue && type != null &&
        (type.equals(value.getType()) ||
         TypeConversionUtil.isSafeConversion(type, value.getType()) && 
         TypeConversionUtil.isSafeConversion(PsiType.INT, type))) {
      return value;
    }

    DfType dfType = state.getDfType(value);
    if (dfType instanceof DfConstantType && type != null) {
      Object casted = TypeConversionUtil.computeCastTo(((DfConstantType<?>)dfType).getValue(), type);
      return factory.getConstant(casted, type);
    }
    if (TypeConversionUtil.isIntegralNumberType(type)) {
      LongRangeSet range = DfLongType.extractRange(dfType);
      return factory.fromDfType(rangeClamped(range.castTo(type), PsiType.LONG.equals(type)));
    }
    return factory.getUnknown();
  }

  @Override
  public String toString() {
    return "CONVERT_PRIMITIVE";
  }
}
