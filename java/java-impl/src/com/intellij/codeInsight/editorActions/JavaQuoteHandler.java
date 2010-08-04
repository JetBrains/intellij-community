/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class JavaQuoteHandler extends SimpleTokenSetQuoteHandler implements JavaLikeQuoteHandler {
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

  public boolean isAppropriateElementTypeForLiteral(final @NotNull IElementType tokenType) {
    return isAppropriateElementTypeForLiteralStatic(tokenType);
  }

  public boolean needParenthesesAroundConcatenation(final PsiElement element) {
    // example code: "some string".length() must become ("some" + " string").length()
    return element.getParent() instanceof PsiLiteralExpression && element.getParent().getParent() instanceof PsiReferenceExpression;
  }

  public static boolean isAppropriateElementTypeForLiteralStatic(final IElementType tokenType) {
    return ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(tokenType)
              || tokenType == JavaTokenType.SEMICOLON
              || tokenType == JavaTokenType.COMMA
              || tokenType == JavaTokenType.RPARENTH
              || tokenType == JavaTokenType.RBRACKET
              || tokenType == JavaTokenType.RBRACE
              || tokenType == JavaTokenType.STRING_LITERAL
              || tokenType == JavaTokenType.CHARACTER_LITERAL;
  }
}
