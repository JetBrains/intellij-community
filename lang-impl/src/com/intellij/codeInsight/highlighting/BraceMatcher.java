package com.intellij.codeInsight.highlighting;

import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BraceMatcher {
  int getTokenGroup(IElementType tokenType);

  boolean isLBraceToken(HighlighterIterator iterator,CharSequence fileText, FileType fileType);
  boolean isRBraceToken(HighlighterIterator iterator,CharSequence fileText, FileType fileType);
  boolean isPairBraces(IElementType tokenType,IElementType tokenType2);
  boolean isStructuralBrace(HighlighterIterator iterator,CharSequence text, FileType fileType);
  IElementType getTokenType(char ch, HighlighterIterator iterator);
  boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType);

  boolean isStrictTagMatching(final FileType fileType, final int group);
  boolean areTagsCaseSensitive(FileType fileType, int tokenGroup);

  @Nullable
  String getTagName(CharSequence fileText, HighlighterIterator iterator);
}
