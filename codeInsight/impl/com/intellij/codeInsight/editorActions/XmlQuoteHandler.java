/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.xml.XmlTokenType;

/**
 * @author peter
*/
public class XmlQuoteHandler implements TypedHandler.QuoteHandler {
  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    return iterator.getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER;
  }

  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    return iterator.getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER;
  }

  public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
    return true;
  }

  public boolean isInsideLiteral(HighlighterIterator iterator) {
    return false;
  }
}
