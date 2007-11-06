package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.Editor;

public interface QuoteHandler {
  boolean isClosingQuote(HighlighterIterator iterator, int offset);
  boolean isOpeningQuote(HighlighterIterator iterator, int offset);
  boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset);
  boolean isInsideLiteral(HighlighterIterator iterator);
}