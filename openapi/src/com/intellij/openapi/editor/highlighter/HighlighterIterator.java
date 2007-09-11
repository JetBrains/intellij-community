package com.intellij.openapi.editor.highlighter;

import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.tree.IElementType;


public interface HighlighterIterator {
  TextAttributes getTextAttributes();
  int getStart();
  int getEnd();
  IElementType getTokenType();
  void advance();
  void retreat();
  boolean atEnd();
}
