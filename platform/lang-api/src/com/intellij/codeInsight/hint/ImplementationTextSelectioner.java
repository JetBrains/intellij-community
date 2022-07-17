// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.hint;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Declares document range to be shown in quick definition popup ("View | Quick Definition" action) for given {@link PsiElement}.
 * <p/>
 * Register in {@code com.intellij.lang.implementationTextSelectioner} language extension.
 */
public interface ImplementationTextSelectioner {
  /**
   * @return start text offset in the document which corresponds to the {@code element}
   */
  int getTextStartOffset(@NotNull PsiElement element);

  /**
   * @return end text offset in the document which corresponds to the {@code element}
   */
  int getTextEndOffset(@NotNull PsiElement element);
}