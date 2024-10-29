// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
public final class CustomFileTypeBraceMatcher implements PairedBraceMatcher {
  private static final BracePair[] PAIRS = new BracePair[]{
    new BracePair(L_BRACKET, R_BRACKET, true),
    new BracePair(L_ANGLE, R_ANGLE, true),
    new BracePair(L_PARENTH, R_PARENTH, true),
    new BracePair(L_BRACE, R_BRACE, true),
  };

  @Override
  public BracePair @NotNull [] getPairs() {
    return PAIRS;
  }

  @Override
  public boolean isPairedBracesAllowedBeforeType(final @NotNull IElementType lbraceType, final @Nullable IElementType contextType) {
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

  public static final PairedBraceMatcherAdapter INSTANCE = new PairedBraceMatcherAdapter(new CustomFileTypeBraceMatcher(), IDENTIFIER.getLanguage()) {
    @Override
    public int getBraceTokenGroupId(@NotNull IElementType tokenType) {
      int id = super.getBraceTokenGroupId(tokenType);
      return id == -1 ? -1 : 777;
    }
  };
}
