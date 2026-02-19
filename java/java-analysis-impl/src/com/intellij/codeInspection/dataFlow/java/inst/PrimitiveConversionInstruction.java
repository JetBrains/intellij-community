// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.EvalInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfIntType;
import com.intellij.codeInspection.dataFlow.types.DfLongType;
import com.intellij.codeInspection.dataFlow.types.DfPrimitiveType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaBinOpValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A unary instruction that converts a primitive value from the stack to the desired type
 */
public class PrimitiveConversionInstruction extends EvalInstruction {
  private final @NotNull PsiPrimitiveType myTargetType;

  public PrimitiveConversionInstruction(@NotNull PsiPrimitiveType targetType, @Nullable DfaAnchor anchor) {
    super(anchor, 1);
    myTargetType = targetType;
  }

  @Override
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory,
                                @NotNull DfaMemoryState state,
                                @NotNull DfaValue @NotNull ... arguments) {
    DfaValue value = arguments[0];
    if (value instanceof DfaBinOpValue) {
      value = tryReduceOnCast(((DfaBinOpValue)value), state, myTargetType);
    }

    DfType dfType = state.getDfType(value);
    if (value instanceof DfaVariableValue && dfType instanceof DfIntType) {
      LongRangeSet set = JvmPsiRangeSetUtil.typeRange(myTargetType);
      if (set != null && !LongRangeSet.all().equals(set) && ((DfIntType)dfType).meetRange(set).equals(dfType)) {
        return value;
      }
    }
    if (dfType instanceof DfPrimitiveType) {
      return factory.fromDfType(((DfPrimitiveType)dfType).castTo(myTargetType));
    }
    return factory.getUnknown();
  }

  @Override
  public String toString() {
    return "CONVERT_PRIMITIVE " + myTargetType.getPresentableText();
  }

  private static @NotNull DfaValue tryReduceOnCast(@NotNull DfaBinOpValue value,
                                                   @NotNull DfaMemoryState state,
                                                   @NotNull PsiPrimitiveType type) {
    if (!TypeConversionUtil.isIntegralNumberType(type)) return value;
    LongRangeBinOp operation = value.getOperation();
    if ((operation == LongRangeBinOp.PLUS || operation == LongRangeBinOp.MINUS) &&
        JvmPsiRangeSetUtil.castTo(DfLongType.extractRange(state.getDfType(value.getRight())), type).equals(LongRangeSet.point(0))) {
      return value.getLeft();
    }
    if (operation == LongRangeBinOp.PLUS &&
        JvmPsiRangeSetUtil.castTo(DfLongType.extractRange(state.getDfType(value.getLeft())), type).equals(LongRangeSet.point(0))) {
      return value.getRight();
    }
    return value;
  }
}
