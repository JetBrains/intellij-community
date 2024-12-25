// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiTypes;
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
    if (type.equals(PsiTypes.doubleType()) || type.equals(PsiTypes.floatType())) {
      // No widening support for doubles, so we translate the wide range 
      // to avoid diverged states if (int)(double)x cast is performed
      LongRangeSet range = getWideRange();
      if (range.isEmpty()) return DfType.BOTTOM;
      return type.equals(PsiTypes.doubleType()) ? 
             DfTypes.doubleRange(range.min(), range.max()) :
             DfTypes.floatRange(range.min(), range.max());
    }
    if (!TypeConversionUtil.isIntegralNumberType(type)) {
      return DfPrimitiveType.super.castTo(type);
    }
    LongRangeSet range = JvmPsiRangeSetUtil.castTo(getRange(), type);
    LongRangeSet wideRange = JvmPsiRangeSetUtil.castTo(getWideRange(), type);
    return type.equals(PsiTypes.longType()) ? DfTypes.longRange(range, wideRange) : DfTypes.intRange(range, wideRange);
  }

  @Override
  default @NotNull DfType fromRelation(@NotNull RelationType relationType) {
    if (relationType == RelationType.IS || relationType == RelationType.IS_NOT) {
      return DfType.TOP;
    }
    return DfTypes.rangeClamped(getRange().fromRelation(relationType), getLongRangeType());
  }

  @Override
  default boolean isSuperType(@NotNull DfType other) {
    if (other == DfType.BOTTOM) return true;
    if (!(other instanceof DfIntegralType integralType)) return false;
    if (integralType.getLongRangeType() != getLongRangeType()) return false;
    return getRange().contains(integralType.getRange()) &&
           getWideRange().contains(integralType.getWideRange());
  }

  @Override
  default @NotNull DfType join(@NotNull DfType other) {
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

  @Override
  default @NotNull DfType meet(@NotNull DfType other) {
    if (other == DfType.TOP) return this;
    if (!(other instanceof DfIntegralType)) return DfType.BOTTOM;
    if (((DfIntegralType)other).getLongRangeType() != getLongRangeType()) return DfType.BOTTOM;
    LongRangeSet range = ((DfIntegralType)other).getRange().meet(getRange());
    LongRangeSet wideRange = ((DfIntegralType)other).getWideRange().meet(getWideRange());
    return DfTypes.range(range, wideRange, getLongRangeType());
  }

  @Override
  default @NotNull DfType meetRange(@NotNull LongRangeSet range) {
    LongRangeSet resultRange = range.meet(getRange());
    LongRangeSet wideRange = range.meet(getWideRange());
    return DfTypes.range(resultRange, wideRange, getLongRangeType());
  }

  @Override
  default DfType widen() {
    LongRangeSet wideRange = getWideRange();
    return wideRange.equals(getRange()) ? this : DfTypes.range(wideRange, null, getLongRangeType());
  }

  @Override
  default @Nullable DfType tryNegate() {
    LongRangeSet range = getRange();
    LongRangeSet res = getLongRangeType().fullRange().subtract(range);
    return res.intersects(range) ? null : DfTypes.range(res, null, getLongRangeType());
  }
}
