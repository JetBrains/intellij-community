// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * {@link HighlightInfo} with {@link InvalidPsi#psiElement()} during visiting which the info was created
 * Needed for maintaining invariant that all {@link com.intellij.openapi.editor.markup.RangeHighlighter}s created in GHP/LIP must be in sync with {@link HighlightInfoUpdater},
 * meaning that each live highlighter must be referenced from the corresponding {@link HighlightInfoUpdaterImpl.ToolHighlights#elementHighlights} and vice versa
 */
record InvalidPsi(@NotNull PsiElement psiElement, @NotNull HighlightInfo info) {
  @Override
  public String toString() {
    return "InvalidPsi("+psiElement()+(psiElement().isValid() ? "" : "(invalid)")+","+info+")";
  }
}
