// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.TextAttributesKey;

/**
 * Base highlighter colors for multiple languages.
 */
public final class DefaultLanguageHighlighterColors {
  public static final TextAttributesKey TEMPLATE_LANGUAGE_COLOR = TextAttributesKey.createTextAttributesKey("DEFAULT_TEMPLATE_LANGUAGE_COLOR", HighlighterColors.TEXT);
  public static final TextAttributesKey IDENTIFIER = TextAttributesKey.createTextAttributesKey("DEFAULT_IDENTIFIER", HighlighterColors.TEXT);
  public static final TextAttributesKey NUMBER = TextAttributesKey.createTextAttributesKey("DEFAULT_NUMBER");
  public static final TextAttributesKey KEYWORD = TextAttributesKey.createTextAttributesKey("DEFAULT_KEYWORD");
  public static final TextAttributesKey STRING = TextAttributesKey.createTextAttributesKey("DEFAULT_STRING");
  public static final TextAttributesKey BLOCK_COMMENT = TextAttributesKey.createTextAttributesKey("DEFAULT_BLOCK_COMMENT");
  public static final TextAttributesKey LINE_COMMENT = TextAttributesKey.createTextAttributesKey("DEFAULT_LINE_COMMENT");
  public static final TextAttributesKey DOC_COMMENT = TextAttributesKey.createTextAttributesKey("DEFAULT_DOC_COMMENT");
  public static final TextAttributesKey OPERATION_SIGN = TextAttributesKey.createTextAttributesKey("DEFAULT_OPERATION_SIGN");
  public static final TextAttributesKey BRACES = TextAttributesKey.createTextAttributesKey("DEFAULT_BRACES");
  public static final TextAttributesKey DOT = TextAttributesKey.createTextAttributesKey("DEFAULT_DOT");
  public static final TextAttributesKey SEMICOLON = TextAttributesKey.createTextAttributesKey("DEFAULT_SEMICOLON");
  public static final TextAttributesKey COMMA = TextAttributesKey.createTextAttributesKey("DEFAULT_COMMA");
  public static final TextAttributesKey PARENTHESES = TextAttributesKey.createTextAttributesKey("DEFAULT_PARENTHS");
  public static final TextAttributesKey BRACKETS = TextAttributesKey.createTextAttributesKey("DEFAULT_BRACKETS");

  public static final TextAttributesKey LABEL = TextAttributesKey.createTextAttributesKey("DEFAULT_LABEL", IDENTIFIER);
  public static final TextAttributesKey CONSTANT = TextAttributesKey.createTextAttributesKey("DEFAULT_CONSTANT", IDENTIFIER);
  public static final TextAttributesKey LOCAL_VARIABLE = TextAttributesKey.createTextAttributesKey("DEFAULT_LOCAL_VARIABLE", IDENTIFIER);
  public static final TextAttributesKey REASSIGNED_LOCAL_VARIABLE = TextAttributesKey.createTextAttributesKey("DEFAULT_REASSIGNED_LOCAL_VARIABLE", LOCAL_VARIABLE);
  public static final TextAttributesKey GLOBAL_VARIABLE = TextAttributesKey.createTextAttributesKey("DEFAULT_GLOBAL_VARIABLE", IDENTIFIER);

  public static final TextAttributesKey FUNCTION_DECLARATION = TextAttributesKey.createTextAttributesKey("DEFAULT_FUNCTION_DECLARATION", IDENTIFIER);
  public static final TextAttributesKey FUNCTION_CALL = TextAttributesKey.createTextAttributesKey("DEFAULT_FUNCTION_CALL", IDENTIFIER);
  public static final TextAttributesKey PARAMETER = TextAttributesKey.createTextAttributesKey("DEFAULT_PARAMETER", IDENTIFIER);
  public static final TextAttributesKey REASSIGNED_PARAMETER = TextAttributesKey.createTextAttributesKey("DEFAULT_REASSIGNED_PARAMETER", PARAMETER);
  public static final TextAttributesKey CLASS_NAME = TextAttributesKey.createTextAttributesKey("DEFAULT_CLASS_NAME", IDENTIFIER);
  public static final TextAttributesKey INTERFACE_NAME = TextAttributesKey.createTextAttributesKey("DEFAULT_INTERFACE_NAME", IDENTIFIER);
  public static final TextAttributesKey CLASS_REFERENCE = TextAttributesKey.createTextAttributesKey("DEFAULT_CLASS_REFERENCE", IDENTIFIER);
  public static final TextAttributesKey INSTANCE_METHOD = TextAttributesKey.createTextAttributesKey("DEFAULT_INSTANCE_METHOD", FUNCTION_DECLARATION);
  public static final TextAttributesKey INSTANCE_FIELD = TextAttributesKey.createTextAttributesKey("DEFAULT_INSTANCE_FIELD", IDENTIFIER);
  public static final TextAttributesKey STATIC_METHOD = TextAttributesKey.createTextAttributesKey("DEFAULT_STATIC_METHOD", FUNCTION_DECLARATION);
  public static final TextAttributesKey STATIC_FIELD = TextAttributesKey.createTextAttributesKey("DEFAULT_STATIC_FIELD", IDENTIFIER);

  public static final TextAttributesKey DOC_COMMENT_MARKUP = TextAttributesKey.createTextAttributesKey("DEFAULT_DOC_MARKUP");
  public static final TextAttributesKey DOC_COMMENT_TAG = TextAttributesKey.createTextAttributesKey("DEFAULT_DOC_COMMENT_TAG");
  public static final TextAttributesKey DOC_COMMENT_TAG_VALUE = TextAttributesKey.createTextAttributesKey("DEFAULT_DOC_COMMENT_TAG_VALUE");
  public static final ColorKey DOC_COMMENT_GUIDE = ColorKey.createColorKey("DOC_COMMENT_GUIDE");
  public static final ColorKey DOC_COMMENT_LINK = ColorKey.createColorKey("DOC_COMMENT_LINK");
  public static final TextAttributesKey DOC_CODE_INLINE = TextAttributesKey.createTextAttributesKey("DOC_CODE_INLINE");
  public static final TextAttributesKey DOC_CODE_BLOCK = TextAttributesKey.createTextAttributesKey("DOC_CODE_BLOCK");
  public static final TextAttributesKey DOC_TIPS_SHORTCUT = TextAttributesKey.createTextAttributesKey("DOC_TIPS_SHORTCUT");
  public static final TextAttributesKey VALID_STRING_ESCAPE = TextAttributesKey.createTextAttributesKey("DEFAULT_VALID_STRING_ESCAPE");
  public static final TextAttributesKey INVALID_STRING_ESCAPE = TextAttributesKey.createTextAttributesKey("DEFAULT_INVALID_STRING_ESCAPE");

  public static final TextAttributesKey PREDEFINED_SYMBOL = TextAttributesKey.createTextAttributesKey("DEFAULT_PREDEFINED_SYMBOL", IDENTIFIER);
  public static final TextAttributesKey HIGHLIGHTED_REFERENCE = TextAttributesKey.createTextAttributesKey("DEFAULT_HIGHLIGHTED_REFERENCE", STRING);

  public static final TextAttributesKey METADATA = TextAttributesKey.createTextAttributesKey("DEFAULT_METADATA", HighlighterColors.TEXT);

  public static final TextAttributesKey MARKUP_TAG = TextAttributesKey.createTextAttributesKey("DEFAULT_TAG", HighlighterColors.TEXT);
  public static final TextAttributesKey MARKUP_ATTRIBUTE = TextAttributesKey.createTextAttributesKey("DEFAULT_ATTRIBUTE", IDENTIFIER);
  public static final TextAttributesKey MARKUP_ENTITY = TextAttributesKey.createTextAttributesKey("DEFAULT_ENTITY", IDENTIFIER);
  public static final TextAttributesKey INLINE_SUGGESTION = TextAttributesKey.createTextAttributesKey("INLINE_SUGGESTION");
  public static final TextAttributesKey INLINE_PARAMETER_HINT = TextAttributesKey.createTextAttributesKey("INLINE_PARAMETER_HINT");
  public static final TextAttributesKey INLINE_PARAMETER_HINT_HIGHLIGHTED = TextAttributesKey.createTextAttributesKey("INLINE_PARAMETER_HINT_HIGHLIGHTED");
  public static final TextAttributesKey INLINE_PARAMETER_HINT_CURRENT = TextAttributesKey.createTextAttributesKey("INLINE_PARAMETER_HINT_CURRENT");
  public static final TextAttributesKey INLAY_DEFAULT = TextAttributesKey.createTextAttributesKey("INLAY_DEFAULT");
  public static final TextAttributesKey INLAY_BUTTON_DEFAULT = TextAttributesKey.createTextAttributesKey("INLAY_BUTTON_DEFAULT");
  public static final TextAttributesKey INLAY_BUTTON_FOCUSED = TextAttributesKey.createTextAttributesKey("INLAY_BUTTON_FOCUSED");
  public static final TextAttributesKey INLAY_BUTTON_HOVERED = TextAttributesKey.createTextAttributesKey("INLAY_BUTTON_HOVERED");
  public static final TextAttributesKey AI_INLAY_BUTTON_DEFAULT = TextAttributesKey.createTextAttributesKey("AI_INLAY_BUTTON_DEFAULT");
  public static final TextAttributesKey AI_INLAY_BUTTON_FOCUSED = TextAttributesKey.createTextAttributesKey("AI_INLAY_BUTTON_FOCUSED");
  public static final TextAttributesKey AI_INLAY_BUTTON_HOVERED = TextAttributesKey.createTextAttributesKey("AI_INLAY_BUTTON_HOVERED");
  public static final TextAttributesKey INLAY_BUTTON_HINT = TextAttributesKey.createTextAttributesKey("INLAY_BUTTON_HINT");
  public static final TextAttributesKey INLAY_TEXT_WITHOUT_BACKGROUND = TextAttributesKey.createTextAttributesKey("INLAY_TEXT_WITHOUT_BACKGROUND");
  public static final ColorKey INLINE_REFACTORING_SETTINGS_DEFAULT = ColorKey.createColorKey("INLINE_REFACTORING_SETTINGS_DEFAULT");
  public static final ColorKey INLINE_REFACTORING_SETTINGS_FOCUSED = ColorKey.createColorKey("INLINE_REFACTORING_SETTINGS_FOCUSED");
  public static final ColorKey INLINE_REFACTORING_SETTINGS_HOVERED = ColorKey.createColorKey("INLINE_REFACTORING_SETTINGS_HOVERED");
}
