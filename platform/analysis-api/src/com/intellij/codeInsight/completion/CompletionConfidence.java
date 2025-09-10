// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

/**
 * Allows skipping completion autopopup according to the current context.
 * <p/>
 * Register an implementation of this class on the {@code com.intellij.completion.confidence} extension point.
 */
public abstract class CompletionConfidence {

  /**
   * Invoked first when a completion autopopup is scheduled. Extensions are able to cancel this completion process based on location.
   * For example, in string literals or comments, completion autopopup may do more harm than good.
   *
   * @deprecated use {@link #shouldSkipAutopopup(Editor, PsiElement, PsiFile, int)}. It provides information about the current editor.
   */
  @Deprecated
  public @NotNull ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    return ThreeState.UNSURE;
  }

  /**
   * Invoked first when a completion autopopup is scheduled. Extensions are able to cancel this completion process based on location.
   * For example, in string literals or comments, completion autopopup may do more harm than good.
   */
  public @NotNull ThreeState shouldSkipAutopopup(@NotNull Editor editor,
                                                 @NotNull PsiElement contextElement,
                                                 @NotNull PsiFile psiFile,
                                                 int offset) {
    return shouldSkipAutopopup(contextElement, psiFile, offset);
  }
}
