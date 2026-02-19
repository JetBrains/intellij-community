// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  default @Nullable PsiElement getElementByReference(@NotNull PsiReference ref, final int flags) {
    return null;
  }
}
