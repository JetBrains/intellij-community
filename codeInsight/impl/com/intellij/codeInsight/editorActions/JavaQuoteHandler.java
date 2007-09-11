/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * @author peter
*/
public class JavaQuoteHandler extends TypedHandler.SimpleTokenSetQuoteHandler implements TypedHandler.JavaLikeQuoteHandler {
  private final TokenSet concatenatableStrings;

  public JavaQuoteHandler() {
    super(new IElementType[] { JavaTokenType.STRING_LITERAL, JavaTokenType.CHARACTER_LITERAL});
    concatenatableStrings = TokenSet.create(JavaTokenType.STRING_LITERAL);
  }

  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    boolean openingQuote = super.isOpeningQuote(iterator, offset);

    if (openingQuote) {
      // check escape next
      if (!iterator.atEnd()) {
        iterator.retreat();

        if (!iterator.atEnd() && StringEscapesTokenTypes.STRING_LITERAL_ESCAPES.contains(iterator.getTokenType())) {
          openingQuote = false;
        }
        iterator.advance();
      }
    }
    return openingQuote;
  }

  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    boolean closingQuote = super.isClosingQuote(iterator, offset);

    if (closingQuote) {
      // check escape next
      if (!iterator.atEnd()) {
        iterator.advance();

        if (!iterator.atEnd() && StringEscapesTokenTypes.STRING_LITERAL_ESCAPES.contains(iterator.getTokenType())) {
          closingQuote = false;
        }
        iterator.retreat();
      }
    }
    return closingQuote;
  }

  public TokenSet getConcatenatableStringTokenTypes() {
    return concatenatableStrings;
  }

  public String getStringConcatenationOperatorRepresentation() {
    return "+";
  }

  public TokenSet getStringTokenTypes() {
    return myLiteralTokenSet;
  }
}
