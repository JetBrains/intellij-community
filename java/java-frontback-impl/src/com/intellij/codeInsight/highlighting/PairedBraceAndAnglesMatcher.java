// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.lang.BracePair;
import com.intellij.lang.Language;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ParentAwareTokenSet;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public abstract class PairedBraceAndAnglesMatcher extends PairedBraceMatcherAdapter {
  private final TokenSet myTokenSetAllowedInsideAngleBrackets;
  private final ParentAwareTokenSet myBasicTokenSetAllowedInsideAngleBrackets;
  private final LanguageFileType myFileType;

  public PairedBraceAndAnglesMatcher(@NotNull PairedBraceMatcher matcher,
                                     @NotNull Language language,
                                     @NotNull LanguageFileType fileType,
                                     @NotNull TokenSet tokenSetAllowedInsideAngleBrackets) {
    super(matcher, language);
    myTokenSetAllowedInsideAngleBrackets = tokenSetAllowedInsideAngleBrackets;
    myBasicTokenSetAllowedInsideAngleBrackets = null;
    myFileType = fileType;
  }

  public PairedBraceAndAnglesMatcher(@NotNull PairedBraceMatcher matcher,
                                     @NotNull Language language,
                                     @NotNull LanguageFileType fileType,
                                     @NotNull ParentAwareTokenSet basicTokenSetAllowedInsideAngleBrackets) {
    super(matcher, language);
    myTokenSetAllowedInsideAngleBrackets = null;
    myBasicTokenSetAllowedInsideAngleBrackets = basicTokenSetAllowedInsideAngleBrackets;
    myFileType = fileType;
  }

  @Override
  public boolean isLBraceToken(@NotNull HighlighterIterator iterator, @NotNull CharSequence fileText, @NotNull FileType fileType) {
    return isBrace(iterator, fileText, fileType, true);
  }

  @Override
  public boolean isRBraceToken(@NotNull HighlighterIterator iterator, @NotNull CharSequence fileText, @NotNull FileType fileType) {
    return isBrace(iterator, fileText, fileType, false);
  }

  public abstract @NotNull IElementType lt();

  public abstract @NotNull IElementType gt();

  private boolean isBrace(HighlighterIterator iterator,
                          CharSequence fileText,
                          FileType fileType,
                          boolean left) {
    final BracePair pair = findPair(left, iterator, fileText, fileType);
    if (pair == null) return false;

    final IElementType opposite = left ? gt() : lt();
    if ((left ? pair.getRightBraceType() : pair.getLeftBraceType()) != opposite) return true;

    if (fileType != myFileType) return false;

    final IElementType braceElementType = left ? lt() : gt();
    int count = 0;
    try {
      int paired = 1;
      while (true) {
        count++;
        if (left) {
          iterator.advance();
        }
        else {
          iterator.retreat();
        }
        if (iterator.atEnd()) break;
        final IElementType tokenType = iterator.getTokenType();
        if (tokenType == opposite) {
          paired--;
          if (paired == 0) return true;
          continue;
        }

        if (tokenType == braceElementType) {
          paired++;
          continue;
        }

        if (
          (myTokenSetAllowedInsideAngleBrackets == null || !myTokenSetAllowedInsideAngleBrackets.contains(tokenType)) &&
          (myBasicTokenSetAllowedInsideAngleBrackets == null || !myBasicTokenSetAllowedInsideAngleBrackets.contains(tokenType))) {
          return false;
        }
      }
      return false;
    }
    finally {
      while (count-- > 0) {
        if (left) {
          iterator.retreat();
        }
        else {
          iterator.advance();
        }
      }
    }
  }
}
