/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;

/**
 * Base highlighter colors for multiple languages.
 *
 * @author Rustam Vishnyakov
 */
public class DefaultLanguageHighlighterColors {

  public final static TextAttributesKey TEMPLATE_LANGUAGE_COLOR =
    TextAttributesKey.createTextAttributesKey("DEFAULT_TEMPLATE_LANGUAGE_COLOR", HighlighterColors.TEXT);
  public final static TextAttributesKey IDENTIFIER =
    TextAttributesKey.createTextAttributesKey("DEFAULT_IDENTIFIER", HighlighterColors.TEXT);
  public final static TextAttributesKey NUMBER =
    TextAttributesKey.createTextAttributesKey("DEFAULT_NUMBER");
  public final static TextAttributesKey KEYWORD =
    TextAttributesKey.createTextAttributesKey("DEFAULT_KEYWORD");
  public final static TextAttributesKey STRING =
    TextAttributesKey.createTextAttributesKey("DEFAULT_STRING");
  public final static TextAttributesKey BLOCK_COMMENT =
    TextAttributesKey.createTextAttributesKey("DEFAULT_BLOCK_COMMENT");
  public final static TextAttributesKey LINE_COMMENT =
    TextAttributesKey.createTextAttributesKey("DEFAULT_LINE_COMMENT");
  public final static TextAttributesKey DOC_COMMENT =
    TextAttributesKey.createTextAttributesKey("DEFAULT_DOC_COMMENT");
  public final static TextAttributesKey OPERATION_SIGN =
    TextAttributesKey.createTextAttributesKey("DEFAULT_OPERATION_SIGN");
  public final static TextAttributesKey BRACES =
    TextAttributesKey.createTextAttributesKey("DEFAULT_BRACES");
  public final static TextAttributesKey DOT =
    TextAttributesKey.createTextAttributesKey("DEFAULT_DOT");
  public final static TextAttributesKey SEMICOLON =
    TextAttributesKey.createTextAttributesKey("DEFAULT_SEMICOLON");
  public final static TextAttributesKey COMMA =
    TextAttributesKey.createTextAttributesKey("DEFAULT_COMMA");
  public final static TextAttributesKey PARENTHESES =
    TextAttributesKey.createTextAttributesKey("DEFAULT_PARENTHS");
  public final static TextAttributesKey BRACKETS =
    TextAttributesKey.createTextAttributesKey("DEFAULT_BRACKETS");

  public final static TextAttributesKey LABEL =
    TextAttributesKey.createTextAttributesKey("DEFAULT_LABEL", IDENTIFIER);
  public final static TextAttributesKey CONSTANT =
    TextAttributesKey.createTextAttributesKey("DEFAULT_CONSTANT", IDENTIFIER);
  public final static TextAttributesKey LOCAL_VARIABLE =
    TextAttributesKey.createTextAttributesKey("DEFAULT_LOCAL_VARIABLE", IDENTIFIER);
  public final static TextAttributesKey GLOBAL_VARIABLE =
    TextAttributesKey.createTextAttributesKey("DEFAULT_GLOBAL_VARIABLE", IDENTIFIER);

  public final static TextAttributesKey FUNCTION_DECLARATION =
    TextAttributesKey.createTextAttributesKey("DEFAULT_FUNCTION_DECLARATION", IDENTIFIER);
  public final static TextAttributesKey FUNCTION_CALL =
    TextAttributesKey.createTextAttributesKey("DEFAULT_FUNCTION_CALL", IDENTIFIER);
  public final static TextAttributesKey PARAMETER =
    TextAttributesKey.createTextAttributesKey("DEFAULT_PARAMETER", IDENTIFIER);
  public final static TextAttributesKey CLASS_NAME =
    TextAttributesKey.createTextAttributesKey("DEFAULT_CLASS_NAME", IDENTIFIER);
  public final static TextAttributesKey INTERFACE_NAME =
    TextAttributesKey.createTextAttributesKey("DEFAULT_INTERFACE_NAME", IDENTIFIER);
  public final static TextAttributesKey CLASS_REFERENCE =
    TextAttributesKey.createTextAttributesKey("DEFAULT_CLASS_REFERENCE", IDENTIFIER);
  public final static TextAttributesKey INSTANCE_METHOD =
    TextAttributesKey.createTextAttributesKey("DEFAULT_INSTANCE_METHOD", FUNCTION_DECLARATION);
  public final static TextAttributesKey INSTANCE_FIELD =
    TextAttributesKey.createTextAttributesKey("DEFAULT_INSTANCE_FIELD", IDENTIFIER);
  public final static TextAttributesKey STATIC_METHOD =
    TextAttributesKey.createTextAttributesKey("DEFAULT_STATIC_METHOD", FUNCTION_DECLARATION);
  public final static TextAttributesKey STATIC_FIELD =
    TextAttributesKey.createTextAttributesKey("DEFAULT_STATIC_FIELD", IDENTIFIER);

  public final static TextAttributesKey DOC_COMMENT_MARKUP =
    TextAttributesKey.createTextAttributesKey("DEFAULT_DOC_MARKUP");
  public final static TextAttributesKey DOC_COMMENT_TAG =
    TextAttributesKey.createTextAttributesKey("DEFAULT_DOC_COMMENT_TAG");
  public final static TextAttributesKey DOC_COMMENT_TAG_VALUE =
    TextAttributesKey.createTextAttributesKey("DEFAULT_DOC_COMMENT_TAG_VALUE");
  public final static TextAttributesKey VALID_STRING_ESCAPE =
    TextAttributesKey.createTextAttributesKey("DEFAULT_VALID_STRING_ESCAPE");
  public final static TextAttributesKey INVALID_STRING_ESCAPE =
    TextAttributesKey.createTextAttributesKey("DEFAULT_INVALID_STRING_ESCAPE");

  public final static TextAttributesKey PREDEFINED_SYMBOL =
    TextAttributesKey.createTextAttributesKey("DEFAULT_PREDEFINED_SYMBOL", IDENTIFIER);

  public final static TextAttributesKey METADATA =
    TextAttributesKey.createTextAttributesKey("DEFAULT_METADATA", HighlighterColors.TEXT);
  
  public final static TextAttributesKey MARKUP_TAG =
    TextAttributesKey.createTextAttributesKey("DEFAULT_TAG", HighlighterColors.TEXT);
  public final static TextAttributesKey MARKUP_ATTRIBUTE =
    TextAttributesKey.createTextAttributesKey("DEFAULT_ATTRIBUTE", IDENTIFIER);
  public final static TextAttributesKey MARKUP_ENTITY =
    TextAttributesKey.createTextAttributesKey("DEFAULT_ENTITY", IDENTIFIER);
}
