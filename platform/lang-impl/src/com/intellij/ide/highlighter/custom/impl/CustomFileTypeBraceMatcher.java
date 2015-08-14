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

import com.intellij.codeInsight.highlighting.PairedBraceMatcherAdapter;
import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.CustomHighlighterTokenType.*;

/**
 * @author Maxim.Mossienko
 */
public class CustomFileTypeBraceMatcher implements PairedBraceMatcher {
  public static final BracePair[] PAIRS = new BracePair[]{
    new BracePair(L_BRACKET, R_BRACKET, true),
    new BracePair(L_ANGLE, R_ANGLE, true),
    new BracePair(L_PARENTH, R_PARENTH, true),
    new BracePair(L_BRACE, R_BRACE, true),
  };

  @Override
  public BracePair[] getPairs() {
    return PAIRS;
  }

  public boolean isPairedBracesAllowedBeforeType(@NotNull final IElementType lbraceType, @Nullable final IElementType contextType) {
    return contextType == PUNCTUATION ||
           contextType == WHITESPACE ||
           isRBraceToken(contextType);
  }

  @Override
  public int getCodeConstructStart(final PsiFile file, final int openingBraceOffset) {
    return openingBraceOffset;
  }

  private static boolean isRBraceToken(IElementType type) {
    for (BracePair pair : PAIRS) {
      if (type == pair.getRightBraceType()) return true;
    }
    return false;
  }

  @NotNull
  public static PairedBraceMatcherAdapter createBraceMatcher() {
    return new PairedBraceMatcherAdapter(new CustomFileTypeBraceMatcher(), IDENTIFIER.getLanguage()) {
      @Override
      public int getBraceTokenGroupId(IElementType tokenType) {
        int id = super.getBraceTokenGroupId(tokenType);
        return id == -1 ? -1 : 777;
      }
    };
  }
}
