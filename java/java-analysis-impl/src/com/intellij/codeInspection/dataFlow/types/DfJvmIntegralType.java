// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

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
    if (!TypeConversionUtil.isIntegralNumberType(type)) {
      return DfPrimitiveType.super.castTo(type);
    }
    LongRangeSet range = JvmPsiRangeSetUtil.castTo(getRange(), type);
    LongRangeSet wideRange = JvmPsiRangeSetUtil.castTo(getWideRange(), type);
    return type.equals(PsiType.LONG) ? DfTypes.longRange(range, wideRange) : DfTypes.intRange(range, wideRange);
  }
}
