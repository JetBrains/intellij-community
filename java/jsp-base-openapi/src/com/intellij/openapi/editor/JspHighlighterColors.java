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
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.colors.TextAttributesKey;

/**
 * @author yole
 */
public class JspHighlighterColors {
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
