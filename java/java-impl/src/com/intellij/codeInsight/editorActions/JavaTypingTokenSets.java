// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.lang.java.parser.ExpressionParser;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.TokenSet;

final class JavaTypingTokenSets {
  private JavaTypingTokenSets() {
  }

  static final TokenSet INVALID_INSIDE_REFERENCE = TokenSet.create(JavaTokenType.SEMICOLON, JavaTokenType.LBRACE, JavaTokenType.RBRACE);

  static final TokenSet UNWANTED_TOKEN_AT_QUESTION =
    TokenSet.create(JavaTokenType.C_STYLE_COMMENT, JavaTokenType.END_OF_LINE_COMMENT, JavaTokenType.CHARACTER_LITERAL,
                    JavaTokenType.STRING_LITERAL, JavaTokenType.TEXT_BLOCK_LITERAL);

  static final TokenSet UNWANTED_TOKEN_BEFORE_QUESTION =
    TokenSet.orSet(ExpressionParser.ASSIGNMENT_OPS, TokenSet.create(JavaTokenType.QUEST, JavaTokenType.COLON));

  static final TokenSet WANTED_TOKEN_BEFORE_QUESTION =
    // Tokens that may appear before ?: in polyadic expression that may have non-boolean result
    TokenSet.orSet(
      TokenSet.create(JavaTokenType.OR, JavaTokenType.XOR, JavaTokenType.AND),
      ExpressionParser.SHIFT_OPS, ExpressionParser.ADDITIVE_OPS, ExpressionParser.MULTIPLICATIVE_OPS);
}
