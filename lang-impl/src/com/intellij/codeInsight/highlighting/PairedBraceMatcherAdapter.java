package com.intellij.codeInsight.highlighting;

import com.intellij.lang.PairedBraceMatcher;
import com.intellij.lang.Language;
import com.intellij.lang.BracePair;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class PairedBraceMatcherAdapter implements BraceMatcher {
  private PairedBraceMatcher myMatcher;
  private Language myLanguage;

  public PairedBraceMatcherAdapter(final PairedBraceMatcher matcher, Language language) {
    myMatcher = matcher;
    myLanguage = language;
  }

  public int getBraceTokenGroupId(IElementType tokenType) {
    final BracePair[] pairs = myMatcher.getPairs();
    for (BracePair pair : pairs) {
      if (tokenType == pair.getLeftBraceType() || tokenType == pair.getRightBraceType()) return myLanguage.hashCode();
    }
    return -1;
  }

  public boolean isLBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
    final IElementType tokenType = iterator.getTokenType();
    final BracePair[] pairs = myMatcher.getPairs();
    for (BracePair pair : pairs) {
      if (tokenType == pair.getLeftBraceType()) return true;
    }
    return false;
  }

  public boolean isRBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
    final IElementType tokenType = iterator.getTokenType();
    final BracePair[] pairs = myMatcher.getPairs();
    for (BracePair pair : pairs) {
      if (tokenType == pair.getRightBraceType()) return true;
    }
    return false;
  }

  public IElementType getOppositeBraceTokenType(@NotNull final IElementType type) {
    final BracePair[] pairs = myMatcher.getPairs();
    for (BracePair pair : pairs) {
      if (type == pair.getRightBraceType()) return pair.getLeftBraceType();
      if (type == pair.getLeftBraceType()) return pair.getRightBraceType();
    }

    return null;
  }

  public boolean isPairBraces(IElementType tokenType, IElementType tokenType2) {
    final BracePair[] pairs = myMatcher.getPairs();
    for (BracePair pair : pairs) {
      if (tokenType == pair.getLeftBraceType() && tokenType2 == pair.getRightBraceType() ||
          tokenType == pair.getRightBraceType() && tokenType2 == pair.getLeftBraceType()) {
        return true;
      }
    }
    return false;
  }

  public boolean isStructuralBrace(HighlighterIterator iterator, CharSequence text, FileType fileType) {
    final IElementType tokenType = iterator.getTokenType();
    final BracePair[] pairs = myMatcher.getPairs();
    for (BracePair pair : pairs) {
      if (tokenType == pair.getRightBraceType() || tokenType == pair.getLeftBraceType()) return pair.isStructural();
    }
    return false;
  }

  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
    return myMatcher.isPairedBracesAllowedBeforeType(lbraceType, contextType);
  }

  public int getCodeConstructStart(final PsiFile file, int openingBraceOffset) {
    return myMatcher.getCodeConstructStart(file, openingBraceOffset);
  }
}
