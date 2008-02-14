package com.intellij.codeInsight.editorActions;

import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;

public class SimpleTokenSetQuoteHandler implements QuoteHandler {
  protected final TokenSet myLiteralTokenSet;

  public SimpleTokenSetQuoteHandler(IElementType[] _literalTokens) {
    myLiteralTokenSet = TokenSet.create(_literalTokens);
  }

  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();

    if (myLiteralTokenSet.contains(tokenType)){
      int start = iterator.getStart();
      int end = iterator.getEnd();
      return end - start >= 1 && offset == end - 1;
    }

    return false;
  }

  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    if (myLiteralTokenSet.contains(iterator.getTokenType())){
      int start = iterator.getStart();
      return offset == start;
    }

    return false;
  }

  public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
    try {
      Document doc = editor.getDocument();
      CharSequence chars = doc.getCharsSequence();
      int lineEnd = doc.getLineEndOffset(doc.getLineNumber(offset));

      while (!iterator.atEnd() && iterator.getStart() < lineEnd) {
        IElementType tokenType = iterator.getTokenType();

        if (myLiteralTokenSet.contains(tokenType)) {
          if (iterator.getStart() >= iterator.getEnd() - 1 ||
              chars.charAt(iterator.getEnd() - 1) != '\"' && chars.charAt(iterator.getEnd() - 1) != '\'') {
            return true;
          }
        }
        iterator.advance();
      }
    }
    finally {
      while(iterator.atEnd() || iterator.getStart() != offset) iterator.retreat();
    }

    return false;
  }

  public boolean isInsideLiteral(HighlighterIterator iterator) {
    return myLiteralTokenSet.contains(iterator.getTokenType());
  }
}
