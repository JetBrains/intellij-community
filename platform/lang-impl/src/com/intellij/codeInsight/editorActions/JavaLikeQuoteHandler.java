package com.intellij.codeInsight.editorActions;

import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface JavaLikeQuoteHandler extends QuoteHandler {
  TokenSet getConcatenatableStringTokenTypes();
  String getStringConcatenationOperatorRepresentation();

  TokenSet getStringTokenTypes();
  boolean isAppropriateElementTypeForLiteral(final @NotNull IElementType tokenType);

  boolean needParenthesesAroundConcatenation(final PsiElement element);
}
