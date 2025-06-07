// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeType;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiTypes;
import org.jetbrains.annotations.NotNull;

public interface DfIntType extends DfJvmIntegralType {
  @Override
  @NotNull
  LongRangeSet getRange();

  @Override
  default @NotNull DfType eval(@NotNull DfType other, @NotNull LongRangeBinOp op) {
    if (!(other instanceof DfIntType || other instanceof DfLongType && op.isShift())) return DfTypes.INT;
    LongRangeSet result = op.eval(getRange(), ((DfIntegralType)other).getRange(), getLongRangeType());
    LongRangeSet wideResult = op.evalWide(getWideRange(), ((DfIntegralType)other).getWideRange(), getLongRangeType());
    return DfTypes.intRange(result, wideResult);
  }

  @Override
  default @NotNull PsiPrimitiveType getPsiType() {
    return PsiTypes.intType();
  }

  static @NotNull LongRangeSet extractRange(@NotNull DfType type) {
    return type instanceof DfIntegralType ? ((DfIntegralType)type).getRange().meet(DfIntRangeType.FULL_RANGE) :
           DfIntRangeType.FULL_RANGE;
  }

  @Override
  default @NotNull LongRangeType getLongRangeType() {
    return LongRangeType.INT32;
  }
}
