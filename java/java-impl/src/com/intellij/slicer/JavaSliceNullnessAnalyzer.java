// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

public class JavaSliceNullnessAnalyzer extends SliceNullnessAnalyzerBase {
  public JavaSliceNullnessAnalyzer() {
    super(JavaSlicerAnalysisUtil.LEAF_ELEMENT_EQUALITY, JavaSliceProvider.getInstance());
  }

  @Override
  protected @NotNull Nullability checkNullability(PsiElement element) {
    if (element instanceof PsiExpression) {
      return NullabilityUtil.getExpressionNullability((PsiExpression)element, true);
    }
    return Nullability.UNKNOWN;
  }
}
