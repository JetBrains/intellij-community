// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Defines boundaries between language tokens which should be used as stops for next/prev-word caret movements.
 * <p>
 * Currently it can be specified as a language-level extension (see {@link LanguageWordBoundaryFilter}).
 */
public class WordBoundaryFilter {
  /**
   * Given types of two distinct subsequent tokens returned by {@link HighlighterIterator#getTokenType()}, says whether a boundary
   * between them should be recognized by 'Move Caret to Prev/Next Word' actions.
   * <p>
   * Default implementation assumes a word boundary between any two distinct tokens. Override this method to adjust the logic to the
   * specifics of particular programming language.
   *
   * @see HighlighterIterator
   * @see EditorHighlighter
   */
  public boolean isWordBoundary(@NotNull IElementType previousTokenType, @NotNull IElementType tokenType) {
    return !Comparing.equal(previousTokenType, tokenType);
  }
}
