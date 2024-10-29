// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.highlighting;

import com.intellij.lang.BracePair;
import com.intellij.lang.Language;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PairedBraceMatcherAdapter implements NontrivialBraceMatcher, PairedBraceMatcher {
  private final PairedBraceMatcher myMatcher;
  private final Language myLanguage;

  public PairedBraceMatcherAdapter(@NotNull PairedBraceMatcher matcher, @NotNull Language language) {
    myMatcher = matcher;
    myLanguage = language;
  }

  @Override
  public BracePair @NotNull [] getPairs() {
    return myMatcher.getPairs();
  }

  @Override
  public int getBraceTokenGroupId(@NotNull IElementType tokenType) {
    final BracePair[] pairs = myMatcher.getPairs();
    for (BracePair pair : pairs) {
      if (tokenType == pair.getLeftBraceType() || tokenType == pair.getRightBraceType()) return myLanguage.hashCode();
    }
    return -1;
  }

  public @Nullable BracePair findPair(boolean left, HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
    final IElementType tokenType = iterator.getTokenType();
    final BracePair[] pairs = myMatcher.getPairs();
    for (BracePair pair : pairs) {
      if (tokenType == (left ? pair.getLeftBraceType() : pair.getRightBraceType())) return pair;
    }
    return null;
  }

  @Override
  public boolean isLBraceToken(@NotNull HighlighterIterator iterator, @NotNull CharSequence fileText, @NotNull FileType fileType) {
    return findPair(true, iterator, fileText, fileType) != null;
  }

  @Override
  public boolean isRBraceToken(@NotNull HighlighterIterator iterator, @NotNull CharSequence fileText, @NotNull FileType fileType) {
    return findPair(false, iterator, fileText, fileType) != null;
  }

  @Override
  public IElementType getOppositeBraceTokenType(final @NotNull IElementType type) {
    final BracePair[] pairs = myMatcher.getPairs();
    for (BracePair pair : pairs) {
      if (type == pair.getRightBraceType()) return pair.getLeftBraceType();
      if (type == pair.getLeftBraceType()) return pair.getRightBraceType();
    }

    return null;
  }

  @Override
  public boolean isPairBraces(@NotNull IElementType tokenType, @NotNull IElementType tokenType2) {
    final BracePair[] pairs = myMatcher.getPairs();
    for (BracePair pair : pairs) {
      if (tokenType == pair.getLeftBraceType() && tokenType2 == pair.getRightBraceType() ||
          tokenType == pair.getRightBraceType() && tokenType2 == pair.getLeftBraceType()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isStructuralBrace(@NotNull HighlighterIterator iterator, @NotNull CharSequence text, @NotNull FileType fileType) {
    final IElementType tokenType = iterator.getTokenType();
    final BracePair[] pairs = myMatcher.getPairs();
    for (BracePair pair : pairs) {
      if (tokenType == pair.getRightBraceType() || tokenType == pair.getLeftBraceType()) return pair.isStructural();
    }
    return false;
  }

  @Override
  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
    return myMatcher.isPairedBracesAllowedBeforeType(lbraceType, contextType);
  }

  @Override
  public int getCodeConstructStart(final @NotNull PsiFile file, int openingBraceOffset) {
    return myMatcher.getCodeConstructStart(file, openingBraceOffset);
  }

  @Override
  public @NotNull List<IElementType> getOppositeBraceTokenTypes(@NotNull IElementType type) {
    List<IElementType> result = null;

    for (BracePair pair : myMatcher.getPairs()) {
      IElementType match = null;

      if (type == pair.getRightBraceType()) match = pair.getLeftBraceType();
      if (type == pair.getLeftBraceType()) match = pair.getRightBraceType();

      if (match != null) {
        if (result == null) result = new ArrayList<>(2);
        result.add(match);
      }
    }

    return result != null ? result : Collections.emptyList();
  }

  @Override
  public boolean shouldStopMatch(boolean forward, @NotNull IElementType braceType, @NotNull HighlighterIterator iterator) {
    if (myMatcher instanceof BraceMatcherTerminationAspect) {
      return ((BraceMatcherTerminationAspect)myMatcher).shouldStopMatch(forward, braceType, iterator);
    }
    return false;
  }
}
