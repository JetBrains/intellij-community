// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.psi.PsiTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

final class DfIntRangeType extends DfAbstractRangeType implements DfIntType {
  static final @NotNull LongRangeSet FULL_RANGE = Objects.requireNonNull(JvmPsiRangeSetUtil.typeRange(PsiTypes.intType()));

  DfIntRangeType(@NotNull LongRangeSet range, @Nullable LongRangeSet wideRange) {
    super(checkRange(range), wideRange);
  }

  private static @NotNull LongRangeSet checkRange(@NotNull LongRangeSet range) {
    if (!FULL_RANGE.contains(range)) {
      throw new IllegalArgumentException("Illegal range supplied for int type: " + range);
    }
    return FULL_RANGE.equals(range) ? FULL_RANGE : range;
  }

  @Override
  public @NotNull String toString() {
    if (getRange() == FULL_RANGE) return JavaKeywords.INT;
    return JavaKeywords.INT + " " + JvmPsiRangeSetUtil.getPresentationText(getRange(), PsiTypes.intType());
  }
}
