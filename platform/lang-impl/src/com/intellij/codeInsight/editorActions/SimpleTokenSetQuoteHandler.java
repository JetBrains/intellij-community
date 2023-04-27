// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public class SimpleTokenSetQuoteHandler implements QuoteHandler {
  protected final TokenSet myLiteralTokenSet;

  public SimpleTokenSetQuoteHandler(IElementType... _literalTokens) {
    this(TokenSet.create(_literalTokens));
  }

  public SimpleTokenSetQuoteHandler(TokenSet tokenSet) {
    myLiteralTokenSet = tokenSet;
  }

  @Override
  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();

    if (myLiteralTokenSet.contains(tokenType)){
      int start = iterator.getStart();
      int end = iterator.getEnd();
      return end - start >= 1 && offset == end - 1;
    }

    return false;
  }

  @Override
  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    if (myLiteralTokenSet.contains(iterator.getTokenType())){
      int start = iterator.getStart();
      return offset == start;
    }

    return false;
  }

  @Override
  public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
    int start = iterator.getStart();
    try {
      Document doc = editor.getDocument();
      CharSequence chars = doc.getCharsSequence();
      int lineEnd = doc.getLineEndOffset(doc.getLineNumber(offset));

      while (!iterator.atEnd() && iterator.getStart() < lineEnd) {
        IElementType tokenType = iterator.getTokenType();

        if (myLiteralTokenSet.contains(tokenType)) {
          if (isNonClosedLiteral(iterator, chars)) return true;
        }
        iterator.advance();
      }
    }
    finally {
      while(iterator.atEnd() || iterator.getStart() != start) iterator.retreat();
    }

    return false;
  }

  protected boolean isNonClosedLiteral(HighlighterIterator iterator, CharSequence chars) {
    if (iterator.getStart() >= iterator.getEnd() - 1 ||
        chars.charAt(iterator.getEnd() - 1) != '\"' && chars.charAt(iterator.getEnd() - 1) != '\'') {
      return true;
    }
    return false;
  }

  @Override
  public boolean isInsideLiteral(HighlighterIterator iterator) {
    return myLiteralTokenSet.contains(iterator.getTokenType());
  }
}
