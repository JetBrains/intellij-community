// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.colors.TextAttributesKey;

/**
 * @author yole
 */
public final class JspHighlighterColors {
  private JspHighlighterColors() { }

  public static final TextAttributesKey JSP_SCRIPTING_BACKGROUND =
    TextAttributesKey.createTextAttributesKey("JSP_SCRIPTING_BACKGROUND", DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR);
  public static final TextAttributesKey JSP_ACTION_AND_DIRECTIVE_BACKGROUND =
    TextAttributesKey.createTextAttributesKey("JSP_DIRECTIVE_BACKGROUND", DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR);
  public static final TextAttributesKey JSP_ACTION_AND_DIRECTIVE_NAME =
    TextAttributesKey.createTextAttributesKey("JSP_DIRECTIVE_NAME", DefaultLanguageHighlighterColors.KEYWORD);
  public static final TextAttributesKey JSP_ATTRIBUTE_NAME =
    TextAttributesKey.createTextAttributesKey("JSP_ATTRIBUTE_NAME", DefaultLanguageHighlighterColors.MARKUP_ATTRIBUTE);
  public static final TextAttributesKey JSP_ATTRIBUTE_VALUE =
    TextAttributesKey.createTextAttributesKey("JSP_ATTRIBUTE_VALUE", DefaultLanguageHighlighterColors.STRING);
  public static final TextAttributesKey JSP_COMMENT =
    TextAttributesKey.createTextAttributesKey("JSP_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT);
  public static final TextAttributesKey JSP_DIRECTIVE_STAT_END_MARKER = DefaultLanguageHighlighterColors.KEYWORD;
}
