package com.intellij.codeInsight.highlighting;

import com.intellij.lang.PairedBraceMatcher;
import com.intellij.lang.Language;
import com.intellij.lang.BracePair;
import com.intellij.psi.tree.IElementType;
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

  public int getTokenGroup(IElementType tokenType) {
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

  public IElementType getTokenType(char ch, HighlighterIterator iterator) {
    if (iterator.atEnd()) return null;
    final IElementType tokenType = iterator.getTokenType();
    if (tokenType.getLanguage() != myLanguage) return null;
    final BracePair[] pairs = myMatcher.getPairs();

    for (final BracePair pair : pairs) {
      if (ch == pair.getRightBraceChar()) return pair.getRightBraceType();
      if (ch == pair.getLeftBraceChar()) return pair.getLeftBraceType();
    }
    return null;
  }

  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
    return myMatcher.isPairedBracesAllowedBeforeType(lbraceType, contextType);
  }

  public boolean isStrictTagMatching(final FileType fileType, final int group) {
    return false;
  }

  public boolean areTagsCaseSensitive(final FileType fileType, final int tokenGroup) {
    return false;
  }
}
