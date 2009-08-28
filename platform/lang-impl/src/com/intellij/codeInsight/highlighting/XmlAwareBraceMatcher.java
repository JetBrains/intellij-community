package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.Nullable;

public interface XmlAwareBraceMatcher extends BraceMatcher {
  boolean isStrictTagMatching(final FileType fileType, final int braceGroupId);
  boolean areTagsCaseSensitive(FileType fileType, final int braceGroupId);

  @Nullable
  String getTagName(CharSequence fileText, HighlighterIterator iterator);
}