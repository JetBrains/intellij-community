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
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.util.containers.HashMap;

import java.util.Map;

/**
 * Base highlighter colors for multiple languages.
 *
 * @author Rustam Vishnyakov
 */
public class DefaultLanguageHighlighterColors {

  private final static Map<TextAttributesKey,String> DISPLAY_NAMES_MAP = new HashMap<TextAttributesKey, String>();

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
  public final static TextAttributesKey LINE_COMMENT =
    TextAttributesKey.createTextAttributesKey("DEFAULT_LINE_COMMENT", SyntaxHighlighterColors.LINE_COMMENT);
  public final static TextAttributesKey OPERATION_SIGN =
    TextAttributesKey.createTextAttributesKey("DEFAULT_OPERATION_SIGN", SyntaxHighlighterColors.OPERATION_SIGN);
  public final static TextAttributesKey BRACES =
    TextAttributesKey.createTextAttributesKey("DEFAULT_BRACES", SyntaxHighlighterColors.BRACES);

  static {
    DISPLAY_NAMES_MAP.put(IDENTIFIER, "Identifier");
    DISPLAY_NAMES_MAP.put(TEMPLATE_LANGUAGE_COLOR, "Template language");
    DISPLAY_NAMES_MAP.put(NUMBER, "Number");
    DISPLAY_NAMES_MAP.put(KEYWORD, "Keyword");
    DISPLAY_NAMES_MAP.put(STRING, "String");
    DISPLAY_NAMES_MAP.put(LINE_COMMENT, "Line comment");
    DISPLAY_NAMES_MAP.put(OPERATION_SIGN, "Operator");
    DISPLAY_NAMES_MAP.put(BRACES, "Braces");
  }

  public static AttributesDescriptor createAttributeDescriptor(TextAttributesKey key) {
    String presentableName = DISPLAY_NAMES_MAP.get(key);
    if (presentableName == null) presentableName = key.getExternalName();
    return new AttributesDescriptor(presentableName, key);
  }

  public static String getDisplayName(TextAttributesKey key) {
    return DISPLAY_NAMES_MAP.containsKey(key) ? DISPLAY_NAMES_MAP.get(key) : "<" + key.getExternalName() +">";
  }
}
