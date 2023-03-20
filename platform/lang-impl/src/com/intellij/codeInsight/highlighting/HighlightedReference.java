// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.psi.PsiReference;

/**
 * Marker interface to highlight references.
 * <p>
 * Use for non-soft references in non-obvious places like String literals which have some navigation target.
 * Highlighting will apply
 * {@link com.intellij.openapi.editor.DefaultLanguageHighlighterColors#HIGHLIGHTED_REFERENCE} text attributes.
 */
public interface HighlightedReference extends PsiReference {
  default boolean isHighlightedWhenSoft() {
    return false;
  }
}