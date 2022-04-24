// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.psi.PsiReference;

/**
 * Marker interface for references that should be highlighted with
 * [com.intellij.openapi.editor.DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE] text attributes.
 */
public interface HighlightedReference extends PsiReference {
  default boolean isHighlightedWhenSoft() {
    return false;
  }
}