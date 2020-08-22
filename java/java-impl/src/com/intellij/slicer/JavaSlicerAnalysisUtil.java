// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaReference;
import org.jetbrains.annotations.NotNull;

public final class JavaSlicerAnalysisUtil {
  public static final SliceLeafEquality LEAF_ELEMENT_EQUALITY = new SliceLeafEquality() {
    @NotNull
    @Override
    protected PsiElement substituteElement(@NotNull PsiElement element) {
      if (element instanceof PsiJavaReference) {
        PsiElement resolved = ((PsiJavaReference)element).resolve();
        if (resolved != null) return resolved;
      }
      return element;
    }
  };

  @NotNull
  public static SliceLeafAnalyzer createLeafAnalyzer() {
    return new SliceLeafAnalyzer(LEAF_ELEMENT_EQUALITY, JavaSliceProvider.getInstance());
  }

  private JavaSlicerAnalysisUtil() {
  }
}
