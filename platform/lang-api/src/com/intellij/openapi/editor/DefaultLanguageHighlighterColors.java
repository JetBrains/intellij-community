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
import com.intellij.util.containers.HashMap;

import java.util.Map;

/**
 * Base highlighter colors for multiple languages.
 *
 * @author Rustam Vishnyakov
 */
@SuppressWarnings("deprecation") // SyntaxHighlighterColors is used for compatibility with old schemes
public class DefaultLanguageHighlighterColors {

  public final static TextAttributesKey TEMPLATE_LANGUAGE_COLOR =
    TextAttributesKey.createTextAttributesKey("DEFAULT_TEMPLATE_LANGUAGE_COLOR");
  public final static TextAttributesKey IDENTIFIER =
    TextAttributesKey.createTextAttributesKey("DEFAULT_IDENTIFIER");
  public final static TextAttributesKey NUMBER =
    TextAttributesKey.createTextAttributesKey("DEFAULT_NUMBER", SyntaxHighlighterColors.NUMBER);
  public final static TextAttributesKey KEYWORD =
    TextAttributesKey.createTextAttributesKey("DEFAULT_KEYWORD", SyntaxHighlighterColors.KEYWORD);
  public final static TextAttributesKey STRING =
    TextAttributesKey.createTextAttributesKey("DEFAULT_STRING", SyntaxHighlighterColors.STRING);
  public final static TextAttributesKey BLOCk_COMMENT =
    TextAttributesKey.createTextAttributesKey("DEFAULT_BLOCK_COMMENT", SyntaxHighlighterColors.JAVA_BLOCK_COMMENT);
  public final static TextAttributesKey LINE_COMMENT =
    TextAttributesKey.createTextAttributesKey("DEFAULT_LINE_COMMENT", SyntaxHighlighterColors.LINE_COMMENT);
  public final static TextAttributesKey DOC_COMMENT =
    TextAttributesKey.createTextAttributesKey("DEFAULT_DOC_COMMENT", SyntaxHighlighterColors.DOC_COMMENT);
  public final static TextAttributesKey OPERATION_SIGN =
    TextAttributesKey.createTextAttributesKey("DEFAULT_OPERATION_SIGN", SyntaxHighlighterColors.OPERATION_SIGN);
  public final static TextAttributesKey BRACES =
    TextAttributesKey.createTextAttributesKey("DEFAULT_BRACES", SyntaxHighlighterColors.BRACES);
  public final static TextAttributesKey DOT =
    TextAttributesKey.createTextAttributesKey("DEFAULT_DOT", SyntaxHighlighterColors.DOT);
  public final static TextAttributesKey SEMICOLON =
    TextAttributesKey.createTextAttributesKey("DEFAULT_SEMICOLON", SyntaxHighlighterColors.JAVA_SEMICOLON);
  public final static TextAttributesKey COMMA =
    TextAttributesKey.createTextAttributesKey("DEFAULT_COMMA", SyntaxHighlighterColors.COMMA);
  public final static TextAttributesKey PARENTHESES =
    TextAttributesKey.createTextAttributesKey("DEFAULT_PARENTHS", SyntaxHighlighterColors.PARENTHS);
  public final static TextAttributesKey BRACKETS =
    TextAttributesKey.createTextAttributesKey("DEFAULT_BRACKETS", SyntaxHighlighterColors.BRACKETS);

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


}
