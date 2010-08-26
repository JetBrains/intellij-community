/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.highlighter.custom.impl;

import com.intellij.codeInsight.highlighting.BraceMatcher;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Maxim.Mossienko
 */
public class CustomFileTypeBraceMatcher implements BraceMatcher {
  public int getBraceTokenGroupId(IElementType tokenType) {
    return 777;
  }

  public boolean isLBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
    final IElementType tokenType = iterator.getTokenType();

    return tokenType == CustomHighlighterTokenType.L_BRACKET ||
           tokenType == CustomHighlighterTokenType.L_ANGLE ||
           tokenType == CustomHighlighterTokenType.L_PARENTH ||
           tokenType == CustomHighlighterTokenType.L_BRACE;
  }

  public boolean isRBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
    return isRBraceToken(iterator.getTokenType());
  }

  private static boolean isRBraceToken(IElementType tokenType) {
    return tokenType == CustomHighlighterTokenType.R_BRACKET ||
           tokenType == CustomHighlighterTokenType.R_ANGLE ||
           tokenType == CustomHighlighterTokenType.R_PARENTH ||
           tokenType == CustomHighlighterTokenType.R_BRACE;
  }

  public boolean isPairBraces(IElementType tokenType, IElementType tokenType2) {
    return (tokenType == CustomHighlighterTokenType.L_BRACE && tokenType2 == CustomHighlighterTokenType.R_BRACE) ||
           (tokenType == CustomHighlighterTokenType.R_BRACE && tokenType2 == CustomHighlighterTokenType.L_BRACE) ||
           (tokenType == CustomHighlighterTokenType.L_BRACKET && tokenType2 == CustomHighlighterTokenType.R_BRACKET) ||
           (tokenType == CustomHighlighterTokenType.R_BRACKET && tokenType2 == CustomHighlighterTokenType.L_BRACKET) ||
           (tokenType == CustomHighlighterTokenType.L_ANGLE && tokenType2 == CustomHighlighterTokenType.R_ANGLE) ||
           (tokenType == CustomHighlighterTokenType.R_ANGLE && tokenType2 == CustomHighlighterTokenType.L_ANGLE) ||
           (tokenType == CustomHighlighterTokenType.L_PARENTH && tokenType2 == CustomHighlighterTokenType.R_PARENTH) ||
           (tokenType == CustomHighlighterTokenType.R_PARENTH && tokenType2 == CustomHighlighterTokenType.L_PARENTH);
  }

  public boolean isStructuralBrace(HighlighterIterator iterator, CharSequence text, FileType fileType) {
    final IElementType type = iterator.getTokenType();
    return type == CustomHighlighterTokenType.L_BRACE || type == CustomHighlighterTokenType.R_BRACE;
  }

  public IElementType getOppositeBraceTokenType(@NotNull IElementType type) {
    if (!(type instanceof CustomHighlighterTokenType.CustomElementType)) {
      return null;
    }

    if (type == CustomHighlighterTokenType.L_BRACE) return CustomHighlighterTokenType.R_BRACE;
    if (type == CustomHighlighterTokenType.R_BRACE) return CustomHighlighterTokenType.L_BRACE;

    if (type == CustomHighlighterTokenType.L_BRACKET) return CustomHighlighterTokenType.R_BRACKET;
    if (type == CustomHighlighterTokenType.R_BRACKET) return CustomHighlighterTokenType.L_BRACKET;
    if (type == CustomHighlighterTokenType.L_ANGLE) return CustomHighlighterTokenType.R_ANGLE;
    if (type == CustomHighlighterTokenType.R_ANGLE) return CustomHighlighterTokenType.L_ANGLE;
    if (type == CustomHighlighterTokenType.L_PARENTH) return CustomHighlighterTokenType.R_PARENTH;
    if (type == CustomHighlighterTokenType.R_PARENTH) return CustomHighlighterTokenType.L_PARENTH;

    return null;
  }

  public boolean isPairedBracesAllowedBeforeType(@NotNull final IElementType lbraceType, @Nullable final IElementType contextType) {
    return contextType == CustomHighlighterTokenType.PUNCTUATION ||
           contextType == CustomHighlighterTokenType.WHITESPACE ||
           isRBraceToken(contextType);
  }

  public int getCodeConstructStart(final PsiFile file, final int openingBraceOffset) {
    return openingBraceOffset;
  }
}
