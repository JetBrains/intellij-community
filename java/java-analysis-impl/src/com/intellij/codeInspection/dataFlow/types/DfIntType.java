// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DfIntType extends DfIntegralType {
  @Override
  @NotNull
  LongRangeSet getRange();

  @Override
  default boolean isSuperType(@NotNull DfType other) {
    if (other == DfTypes.BOTTOM) return true;
    if (!(other instanceof DfIntType)) return false;
    DfIntType intType = (DfIntType)other;
    return getRange().contains(intType.getRange()) &&
           getWideRange().contains(intType.getWideRange());
  }

  @Override
  default boolean containsConstant(@NotNull DfConstantType<?> constant) {
    Integer value = constant.getConstantOfType(Integer.class);
    return value != null && getRange().contains(value);
  }

  @Override
  default @NotNull DfType eval(@NotNull DfType other, @NotNull LongRangeBinOp op) {
    if (!(other instanceof DfIntType || other instanceof DfLongType && op.isShift())) return DfTypes.INT;
    LongRangeSet result = op.eval(getRange(), ((DfIntegralType)other).getRange(), false);
    LongRangeSet wideResult = op.evalWide(getWideRange(), ((DfIntegralType)other).getWideRange(), false);
    return DfTypes.intRange(result, wideResult);
  }

  @NotNull
  @Override
  default DfType join(@NotNull DfType other) {
    if (other == DfTypes.BOTTOM) return this;
    if (!(other instanceof DfIntType)) return DfTypes.TOP;
    LongRangeSet range = ((DfIntType)other).getRange().unite(getRange());
    LongRangeSet wideRange = ((DfIntType)other).getWideRange().unite(getWideRange());
    return DfTypes.intRange(range, wideRange);
  }

  @NotNull
  @Override
  default DfType meet(@NotNull DfType other) {
    if (other == DfTypes.TOP) return this;
    if (!(other instanceof DfIntType)) return DfTypes.BOTTOM;
    LongRangeSet range = ((DfIntType)other).getRange().intersect(getRange());
    LongRangeSet wideRange = ((DfIntType)other).getWideRange().intersect(getWideRange());
    return DfTypes.intRange(range, wideRange);
  }

  @Override
  default DfType widen() {
    LongRangeSet wideRange = getWideRange();
    return wideRange.equals(getRange()) ? this : DfTypes.intRange(wideRange);
  }

  @NotNull
  @Override
  default DfType meetRange(@NotNull LongRangeSet range) {
    return meet(DfTypes.intRangeClamped(range));
  }

  @NotNull
  @Override
  default PsiPrimitiveType getPsiType() {
    return PsiType.INT;
  }

  @Nullable
  @Override
  default DfType tryNegate() {
    LongRangeSet range = getRange();
    LongRangeSet res = DfIntRangeType.FULL_RANGE.subtract(range);
    return res.intersects(range) ? null : DfTypes.intRange(res);
  }

  @NotNull
  static LongRangeSet extractRange(@NotNull DfType type) {
    return type instanceof DfIntegralType ? ((DfIntegralType)type).getRange().intersect(DfIntRangeType.FULL_RANGE) :
           DfIntRangeType.FULL_RANGE;
  }
}
