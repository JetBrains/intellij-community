/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.bidi;

import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Defines boundaries between regions for which bidi layout should be performed independently. This is required e.g. to make sure that
 * programming language elements (e.g. identifiers) are not reordered visually even if they are named using RTL languages.
 * <p>
 * Currently it can be specified as a language-level extension (see {@link LanguageBidiRegionsSeparator}).
 * <p>
 * Default implementation assumes a border between any two tokens of different types.
 */
public abstract class BidiRegionsSeparator {
  /**
   * Given types of two distinct subsequent tokens returned by {@link HighlighterIterator#getTokenType()}, says whether bidi layout 
   * should be performed independently on both sides of the border between tokens.
   * 
   * @see HighlighterIterator
   * @see EditorHighlighter
   */
  public abstract boolean createBorderBetweenTokens(@NotNull IElementType previousTokenType, @NotNull IElementType tokenType);
}
