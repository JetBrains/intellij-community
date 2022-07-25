// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a JVM integral primitive (int or long)
 */
public interface DfJvmIntegralType extends DfIntegralType, DfPrimitiveType {
  /**
   * Cast this type to the specified primitive type
   * @param type target type
   * @return result of the cast
   */
  @Override
  default @NotNull DfType castTo(@NotNull PsiPrimitiveType type) {
    if (getConstantOfType(Object.class) != null) {
      return DfPrimitiveType.super.castTo(type);
    }
    if (type.equals(PsiType.DOUBLE) || type.equals(PsiType.FLOAT)) {
      // No widening support for doubles, so we translate the wide range 
      // to avoid diverged states if (int)(double)x cast is performed
      LongRangeSet range = getWideRange();
      if (range.isEmpty()) return DfType.BOTTOM;
      return type.equals(PsiType.DOUBLE) ? 
             DfTypes.doubleRange(range.min(), range.max()) :
             DfTypes.floatRange(range.min(), range.max());
    }
    if (!TypeConversionUtil.isIntegralNumberType(type)) {
      return DfPrimitiveType.super.castTo(type);
    }
    LongRangeSet range = JvmPsiRangeSetUtil.castTo(getRange(), type);
    LongRangeSet wideRange = JvmPsiRangeSetUtil.castTo(getWideRange(), type);
    return type.equals(PsiType.LONG) ? DfTypes.longRange(range, wideRange) : DfTypes.intRange(range, wideRange);
  }

  @Override
  @NotNull
  default DfType fromRelation(@NotNull RelationType relationType) {
    if (relationType == RelationType.IS || relationType == RelationType.IS_NOT) {
      return DfType.TOP;
    }
    return DfTypes.rangeClamped(getRange().fromRelation(relationType), getLongRangeType());
  }

  @Override
  default boolean isSuperType(@NotNull DfType other) {
    if (other == DfType.BOTTOM) return true;
    if (!(other instanceof DfIntegralType)) return false;
    DfIntegralType integralType = (DfIntegralType)other;
    if (integralType.getLongRangeType() != getLongRangeType()) return false;
    return getRange().contains(integralType.getRange()) &&
           getWideRange().contains(integralType.getWideRange());
  }

  @NotNull
  @Override
  default DfType join(@NotNull DfType other) {
    if (other == DfType.BOTTOM) return this;
    if (!(other instanceof DfIntegralType)) return DfType.TOP;
    if (((DfIntegralType)other).getLongRangeType() != getLongRangeType()) return DfType.TOP;
    LongRangeSet range = ((DfIntegralType)other).getRange().join(getRange());
    LongRangeSet wideRange = ((DfIntegralType)other).getWideRange().join(getWideRange());
    return DfTypes.range(range, wideRange, getLongRangeType());
  }

  @Override
  default @Nullable DfType tryJoinExactly(@NotNull DfType other) {
    if (other == DfType.BOTTOM) return this;
    if (other == DfType.TOP) return other;
    if (!(other instanceof DfIntegralType)) return null;
    if (((DfIntegralType)other).getLongRangeType() != getLongRangeType()) return null;
    LongRangeSet range = ((DfIntegralType)other).getRange().tryJoinExactly(getRange());
    LongRangeSet wideRange = ((DfIntegralType)other).getWideRange().tryJoinExactly(getWideRange());
    if (range == null || wideRange == null) return null;
    return DfTypes.range(range, wideRange, getLongRangeType());
  }

  @NotNull
  @Override
  default DfType meet(@NotNull DfType other) {
    if (other == DfType.TOP) return this;
    if (!(other instanceof DfIntegralType)) return DfType.BOTTOM;
    if (((DfIntegralType)other).getLongRangeType() != getLongRangeType()) return DfType.BOTTOM;
    LongRangeSet range = ((DfIntegralType)other).getRange().meet(getRange());
    LongRangeSet wideRange = ((DfIntegralType)other).getWideRange().meet(getWideRange());
    return DfTypes.range(range, wideRange, getLongRangeType());
  }

  @NotNull
  @Override
  default DfType meetRange(@NotNull LongRangeSet range) {
    LongRangeSet resultRange = range.meet(getRange());
    LongRangeSet wideRange = range.meet(getWideRange());
    return DfTypes.range(resultRange, wideRange, getLongRangeType());
  }

  @Override
  default DfType widen() {
    LongRangeSet wideRange = getWideRange();
    return wideRange.equals(getRange()) ? this : DfTypes.range(wideRange, null, getLongRangeType());
  }

  @Nullable
  @Override
  default DfType tryNegate() {
    LongRangeSet range = getRange();
    LongRangeSet res = getLongRangeType().fullRange().subtract(range);
    return res.intersects(range) ? null : DfTypes.range(res, null, getLongRangeType());
  }
}
