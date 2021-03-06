// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.editor;

import com.intellij.openapi.editor.colors.TextAttributesKey;

/**
 * @author yole
 * @deprecated Use {@link DefaultLanguageHighlighterColors} to inherit default attributes.
 */
@Deprecated
public final class SyntaxHighlighterColors {
  public static final TextAttributesKey LINE_COMMENT = DefaultLanguageHighlighterColors.LINE_COMMENT;
  public static final TextAttributesKey JAVA_BLOCK_COMMENT = DefaultLanguageHighlighterColors.BLOCK_COMMENT;
  public static final TextAttributesKey DOC_COMMENT = DefaultLanguageHighlighterColors.DOC_COMMENT;
  public static final TextAttributesKey KEYWORD = DefaultLanguageHighlighterColors.KEYWORD;
  public static final TextAttributesKey NUMBER = DefaultLanguageHighlighterColors.NUMBER;
  public static final TextAttributesKey STRING = DefaultLanguageHighlighterColors.STRING;
  public static final TextAttributesKey OPERATION_SIGN = DefaultLanguageHighlighterColors.OPERATION_SIGN;
  public static final TextAttributesKey PARENTHS = DefaultLanguageHighlighterColors.PARENTHESES;
  public static final TextAttributesKey BRACKETS = DefaultLanguageHighlighterColors.BRACKETS;
  public static final TextAttributesKey BRACES = DefaultLanguageHighlighterColors.BRACES;
  public static final TextAttributesKey COMMA = DefaultLanguageHighlighterColors.COMMA;
  public static final TextAttributesKey DOT = DefaultLanguageHighlighterColors.DOT;
  public static final TextAttributesKey JAVA_SEMICOLON = DefaultLanguageHighlighterColors.SEMICOLON;
  public static final TextAttributesKey DOC_COMMENT_TAG = DefaultLanguageHighlighterColors.DOC_COMMENT_TAG;
  public static final TextAttributesKey DOC_COMMENT_MARKUP = DefaultLanguageHighlighterColors.DOC_COMMENT_MARKUP;
  public static final TextAttributesKey VALID_STRING_ESCAPE = DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE;
  public static final TextAttributesKey INVALID_STRING_ESCAPE = DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE;
}
