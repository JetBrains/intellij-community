package com.intellij.ide.highlighter.custom;

import com.intellij.openapi.editor.colors.TextAttributesKey;

public interface CustomHighlighterColors {
  TextAttributesKey CUSTOM_KEYWORD1_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("CUSTOM_KEYWORD1_ATTRIBUTES");
  TextAttributesKey CUSTOM_KEYWORD2_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("CUSTOM_KEYWORD2_ATTRIBUTES");
  TextAttributesKey CUSTOM_KEYWORD3_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("CUSTOM_KEYWORD3_ATTRIBUTES");
  TextAttributesKey CUSTOM_KEYWORD4_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("CUSTOM_KEYWORD4_ATTRIBUTES");
  TextAttributesKey CUSTOM_NUMBER_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("CUSTOM_NUMBER_ATTRIBUTES");
  TextAttributesKey CUSTOM_STRING_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("CUSTOM_STRING_ATTRIBUTES");
  TextAttributesKey CUSTOM_LINE_COMMENT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("CUSTOM_LINE_COMMENT_ATTRIBUTES");
  TextAttributesKey CUSTOM_MULTI_LINE_COMMENT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("CUSTOM_MULTI_LINE_COMMENT_ATTRIBUTES");
}
