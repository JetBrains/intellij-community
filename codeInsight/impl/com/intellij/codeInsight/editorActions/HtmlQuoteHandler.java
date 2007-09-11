/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;

/**
 * @author peter
*/
public class HtmlQuoteHandler implements TypedHandler.QuoteHandler {
  private static TypedHandler.QuoteHandler ourStyleQuoteHandler;
  private TypedHandler.QuoteHandler myBaseQuoteHandler;
  private static TypedHandler.QuoteHandler ourScriptQuoteHandler;

  public HtmlQuoteHandler() {
    this(new XmlQuoteHandler());
  }

  public HtmlQuoteHandler(TypedHandler.QuoteHandler _baseHandler) {
    myBaseQuoteHandler = _baseHandler;
  }

  public static void setStyleQuoteHandler(TypedHandler.QuoteHandler quoteHandler) {
    ourStyleQuoteHandler = quoteHandler;
  }

  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    if (myBaseQuoteHandler.isClosingQuote(iterator, offset)) return true;

    if(ourStyleQuoteHandler!=null && ourStyleQuoteHandler.isClosingQuote(iterator, offset)) {
      return true;
    }

    if(ourScriptQuoteHandler!=null && ourScriptQuoteHandler.isClosingQuote(iterator, offset)) {
      return true;
    }
    return false;
  }

  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    if (myBaseQuoteHandler.isOpeningQuote(iterator, offset)) return true;

    if(ourStyleQuoteHandler!=null && ourStyleQuoteHandler.isOpeningQuote(iterator, offset)) {
      return true;
    }

    if(ourScriptQuoteHandler!=null && ourScriptQuoteHandler.isOpeningQuote(iterator, offset)) {
      return true;
    }

    return false;
  }

  public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
    if (myBaseQuoteHandler.hasNonClosedLiteral(editor,iterator, offset)) return true;

    if(ourStyleQuoteHandler!=null && ourStyleQuoteHandler.hasNonClosedLiteral(editor,iterator, offset)) {
      return true;
    }

    if(ourScriptQuoteHandler!=null && ourScriptQuoteHandler.hasNonClosedLiteral(editor,iterator, offset)) {
      return true;
    }

    return false;
  }

  public boolean isInsideLiteral(HighlighterIterator iterator) {
    if (myBaseQuoteHandler.isInsideLiteral(iterator)) return true;

    if(ourStyleQuoteHandler!=null && ourStyleQuoteHandler.isInsideLiteral(iterator)) {
      return true;
    }

    if(ourScriptQuoteHandler!=null && ourScriptQuoteHandler.isInsideLiteral(iterator)) {
      return true;
    }

    return false;
  }

  public static void setScriptQuoteHandler(TypedHandler.QuoteHandler scriptQuoteHandler) {
    ourScriptQuoteHandler = scriptQuoteHandler;
  }
}
