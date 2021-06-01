// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DfLongType extends DfIntegralType {
  @Override
  @NotNull
  LongRangeSet getRange();

  @Override
  default boolean isSuperType(@NotNull DfType other) {
    if (other == DfTypes.BOTTOM) return true;
    if (!(other instanceof DfLongType)) return false;
    DfLongType longType = (DfLongType)other;
    return getRange().contains(longType.getRange()) &&
           getWideRange().contains(longType.getWideRange());
  }

  @Override
  default boolean containsConstant(@NotNull DfConstantType<?> constant) {
    Long value = constant.getConstantOfType(Long.class);
    return value != null && getRange().contains(value);
  }

  @Override
  default @NotNull DfType eval(@NotNull DfType other, @NotNull LongRangeBinOp op) {
    if (!(other instanceof DfLongType || other instanceof DfIntType && op.isShift())) return DfTypes.LONG;
    LongRangeSet result = op.eval(getRange(), ((DfIntegralType)other).getRange(), true);
    LongRangeSet wideResult = op.evalWide(getWideRange(), ((DfIntegralType)other).getWideRange(), true);
    return DfTypes.longRange(result, wideResult);
  }

  @NotNull
  @Override
  default DfType join(@NotNull DfType other) {
    if (other == DfTypes.BOTTOM) return this;
    if (!(other instanceof DfLongType)) return DfTypes.TOP;
    LongRangeSet range = ((DfLongType)other).getRange().unite(getRange());
    LongRangeSet wideRange = ((DfLongType)other).getWideRange().unite(getWideRange());
    return DfTypes.longRange(range, wideRange);
  }

  @NotNull
  @Override
  default DfType meet(@NotNull DfType other) {
    if (other == DfTypes.TOP) return this;
    if (!(other instanceof DfLongType)) return DfTypes.BOTTOM;
    LongRangeSet range = ((DfLongType)other).getRange().intersect(getRange());
    LongRangeSet wideRange = ((DfLongType)other).getWideRange().intersect(getWideRange());
    return DfTypes.longRange(range, wideRange);
  }

  @Override
  default DfType widen() {
    LongRangeSet wideRange = getWideRange();
    return wideRange.equals(getRange()) ? this : DfTypes.longRange(wideRange);
  }

  @NotNull
  @Override
  default DfType meetRange(@NotNull LongRangeSet range) {
    return meet(DfTypes.longRange(range));
  }

  @NotNull
  @Override
  default PsiPrimitiveType getPsiType() {
    return PsiType.LONG;
  }

  @Override
  @Nullable
  default DfType tryNegate() {
    LongRangeSet range = getRange();
    LongRangeSet res = LongRangeSet.all().subtract(range);
    return res.intersects(range) ? null : DfTypes.longRange(res);
  }

  @NotNull
  static LongRangeSet extractRange(@NotNull DfType type) {
    return type instanceof DfIntegralType ? ((DfIntegralType)type).getRange() : LongRangeSet.all();
  }
}
