// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.lang.java.parser.JavaBinaryOperations;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.TokenSet;

public final class JavaTypingTokenSets {
  private JavaTypingTokenSets() {
  }

  static final TokenSet INVALID_INSIDE_REFERENCE = TokenSet.create(JavaTokenType.SEMICOLON, JavaTokenType.LBRACE, JavaTokenType.RBRACE);

  public static final TokenSet UNWANTED_TOKEN_AT_QUESTION =
    TokenSet.create(JavaTokenType.C_STYLE_COMMENT, JavaTokenType.END_OF_LINE_COMMENT, JavaTokenType.CHARACTER_LITERAL,
                    JavaTokenType.STRING_LITERAL, JavaTokenType.TEXT_BLOCK_LITERAL);

  public static final TokenSet UNWANTED_TOKEN_BEFORE_QUESTION =
    TokenSet.orSet(JavaBinaryOperations.ASSIGNMENT_OPS, TokenSet.create(JavaTokenType.QUEST, JavaTokenType.COLON));

  public static final TokenSet WANTED_TOKEN_BEFORE_QUESTION =
    // Tokens that may appear before ?: in polyadic expression that may have non-boolean result
    TokenSet.orSet(
      TokenSet.create(JavaTokenType.OR, JavaTokenType.XOR, JavaTokenType.AND),
      JavaBinaryOperations.SHIFT_OPS, JavaBinaryOperations.ADDITIVE_OPS, JavaBinaryOperations.MULTIPLICATIVE_OPS);
}
