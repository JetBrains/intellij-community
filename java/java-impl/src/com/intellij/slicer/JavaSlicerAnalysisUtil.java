// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaReference;
import org.jetbrains.annotations.NotNull;

public final class JavaSlicerAnalysisUtil {
  public static final SliceLeafEquality LEAF_ELEMENT_EQUALITY = new SliceLeafEquality() {
    @Override
    protected @NotNull PsiElement substituteElement(@NotNull PsiElement element) {
      if (element instanceof PsiJavaReference) {
        PsiElement resolved = ((PsiJavaReference)element).resolve();
        if (resolved != null) return resolved;
      }
      return element;
    }
  };

  public static @NotNull SliceLeafAnalyzer createLeafAnalyzer() {
    return new SliceLeafAnalyzer(LEAF_ELEMENT_EQUALITY, JavaSliceProvider.getInstance());
  }

  private JavaSlicerAnalysisUtil() {
  }
}
