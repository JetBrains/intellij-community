// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A factory for standard formatting model implementations. Not to be used directly
 * by plugins - use {@link FormattingModelProvider} instead.
 *
 * @see FormattingModelProvider
 */
@ApiStatus.Internal
public interface FormattingModelFactory {
  FormattingModel createFormattingModelForPsiFile(PsiFile file,
                                                  @NotNull Block rootBlock,
                                                  CodeStyleSettings settings);

  /**
   * Creates a formatting model with a single root block covering the given {@code PsiElement} with its child elements.
   * The formatter will leave the content inside the block unchanged.
   *
   * @param element The element to create a dummy formatting model for.
   * @return The dummy single-block formatting model covering the given element.
   */
  @NotNull
  FormattingModel createDummyFormattingModel(@NotNull PsiElement element);
}
