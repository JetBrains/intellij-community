// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an integral primitive (int or long)
 */
public interface DfIntegralType extends DfPrimitiveType {
  @NotNull
  LongRangeSet getRange();

  default @NotNull LongRangeSet getWideRange() {
    return getRange();
  }

  /**
   * Perform binary operation between this type and other type
   * @param other other operand
   * @param op operation to perform
   * @return result of the operation
   */
  @NotNull DfType eval(@NotNull DfType other, @NotNull LongRangeBinOp op);

  @NotNull
  default DfType meetRelation(@NotNull RelationType relation, @NotNull DfType other) {
    if (other == DfTypes.TOP) return this;
    if (other instanceof DfIntegralType) {
      return meetRange(((DfIntegralType)other).getRange().fromRelation(relation));
    }
    return DfTypes.BOTTOM;
  }

  @NotNull DfType meetRange(@NotNull LongRangeSet range);

  /**
   * Cast this type to the specified primitive type 
   * @param type target type
   * @return result of the cast
   */
  default @NotNull DfType castTo(@NotNull PsiPrimitiveType type) {
    if (!TypeConversionUtil.isIntegralNumberType(type)) {
      return DfPrimitiveType.super.castTo(type);
    }
    LongRangeSet range = getRange().castTo(type);
    LongRangeSet wideRange = getWideRange().castTo(type);
    return type.equals(PsiType.LONG) ? DfTypes.longRange(range, wideRange) : DfTypes.intRange(range, wideRange);
  }
}
