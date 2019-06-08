// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class JavaQuoteHandler extends SimpleTokenSetQuoteHandler implements JavaLikeQuoteHandler, MultiCharQuoteHandler {
  private final TokenSet concatenatableStrings;

  public JavaQuoteHandler() {
    super(JavaTokenType.STRING_LITERAL, JavaTokenType.CHARACTER_LITERAL, JavaTokenType.RAW_STRING_LITERAL);
    concatenatableStrings = TokenSet.create(JavaTokenType.STRING_LITERAL);
  }

  @Override
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

  @Override
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

  @Override
  public TokenSet getConcatenatableStringTokenTypes() {
    return concatenatableStrings;
  }

  @Override
  public String getStringConcatenationOperatorRepresentation() {
    return "+";
  }

  @Override
  public TokenSet getStringTokenTypes() {
    return myLiteralTokenSet;
  }

  @Override
  public boolean isAppropriateElementTypeForLiteral(final @NotNull IElementType tokenType) {
    return isAppropriateElementTypeForLiteralStatic(tokenType);
  }

  @Override
  public boolean needParenthesesAroundConcatenation(final PsiElement element) {
    // example code: "some string".length() must become ("some" + " string").length()
    return element.getParent() instanceof PsiLiteralExpression && element.getParent().getParent() instanceof PsiReferenceExpression;
  }

  @Nullable
  @Override
  public CharSequence getClosingQuote(@NotNull HighlighterIterator iterator, int offset) {
    if (iterator.getTokenType() == JavaTokenType.RAW_STRING_LITERAL) {
      CharSequence text = iterator.getDocument().getImmutableCharSequence();
      int leadingTicsSequence = PsiRawStringLiteralUtil.getLeadingTicksSequence(text.subSequence(iterator.getStart(), offset));
      if (isOpeningQuote(iterator, offset - leadingTicsSequence)) {
        int closingSequence = PsiRawStringLiteralUtil.getLeadingTicksSequence(text.subSequence(offset, iterator.getEnd()));
        if (closingSequence + 1 == leadingTicsSequence) {
          return "`";
        }
      }
    }
    return null;
  }

  @Override
  public void insertClosingQuote(@NotNull Editor editor, int offset, @NotNull CharSequence closingQuote) {
    editor.getDocument().insertString(offset, " " + closingQuote);
    editor.getSelectionModel().setSelection(offset, offset + 1);
  }

  public static boolean isAppropriateElementTypeForLiteralStatic(final IElementType tokenType) {
    return ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(tokenType)
              || tokenType == JavaTokenType.SEMICOLON
              || tokenType == JavaTokenType.COMMA
              || tokenType == JavaTokenType.RPARENTH
              || tokenType == JavaTokenType.RBRACKET
              || tokenType == JavaTokenType.RBRACE
              || tokenType == JavaTokenType.STRING_LITERAL
              || tokenType == JavaTokenType.CHARACTER_LITERAL
              || tokenType == JavaTokenType.RAW_STRING_LITERAL;
  }
}
