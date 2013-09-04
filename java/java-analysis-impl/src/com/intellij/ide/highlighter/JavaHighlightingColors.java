/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.highlighter;

import com.intellij.openapi.editor.SyntaxHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

/**
 * Highlighting text attributes for Java language.
 * 
 * @author Rustam Vishnyakov
 */
@SuppressWarnings("deprecation")
public class JavaHighlightingColors {
  public static final TextAttributesKey LINE_COMMENT = SyntaxHighlighterColors.LINE_COMMENT;
  public static final TextAttributesKey JAVA_BLOCK_COMMENT = SyntaxHighlighterColors.JAVA_BLOCK_COMMENT;
  public static final TextAttributesKey DOC_COMMENT = SyntaxHighlighterColors.DOC_COMMENT;
  public static final TextAttributesKey KEYWORD = SyntaxHighlighterColors.KEYWORD;
  public static final TextAttributesKey NUMBER = SyntaxHighlighterColors.NUMBER;
  public static final TextAttributesKey STRING = SyntaxHighlighterColors.STRING;
  public static final TextAttributesKey OPERATION_SIGN = SyntaxHighlighterColors.OPERATION_SIGN;
  public static final TextAttributesKey PARENTHESES = SyntaxHighlighterColors.PARENTHS;
  public static final TextAttributesKey BRACKETS = SyntaxHighlighterColors.BRACKETS;
  public static final TextAttributesKey BRACES = SyntaxHighlighterColors.BRACES;
  public static final TextAttributesKey COMMA = SyntaxHighlighterColors.COMMA;
  public static final TextAttributesKey DOT = SyntaxHighlighterColors.DOT;
  public static final TextAttributesKey JAVA_SEMICOLON = SyntaxHighlighterColors.JAVA_SEMICOLON;
  public static final TextAttributesKey DOC_COMMENT_TAG = SyntaxHighlighterColors.DOC_COMMENT_TAG;
  public static final TextAttributesKey DOC_COMMENT_MARKUP = SyntaxHighlighterColors.DOC_COMMENT_MARKUP;
  public static final TextAttributesKey VALID_STRING_ESCAPE = SyntaxHighlighterColors.VALID_STRING_ESCAPE;
  public static final TextAttributesKey INVALID_STRING_ESCAPE = SyntaxHighlighterColors.INVALID_STRING_ESCAPE;

  /** @deprecated use {@link #PARENTHESES} (to remove in IDEA 14) */
  @SuppressWarnings("SpellCheckingInspection")
  public static final TextAttributesKey PARENTHS = PARENTHESES;
}
