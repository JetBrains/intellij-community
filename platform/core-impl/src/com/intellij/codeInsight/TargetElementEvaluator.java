// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author maxim
 */
public interface TargetElementEvaluator {

  boolean includeSelfInGotoImplementation(@NotNull PsiElement element);

  @Nullable
  default PsiElement getElementByReference(@NotNull PsiReference ref, final int flags) {
    return null;
  }
}
