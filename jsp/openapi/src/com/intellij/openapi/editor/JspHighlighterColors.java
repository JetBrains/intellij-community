package com.intellij.openapi.editor;

import com.intellij.openapi.editor.colors.TextAttributesKey;

/**
 * @author yole
 */
public class JspHighlighterColors {
  private JspHighlighterColors() {
  }

  public static final TextAttributesKey JSP_COMMENT = TextAttributesKey.createTextAttributesKey("JSP_COMMENT");
  public static final TextAttributesKey JSP_SCRIPTING_BACKGROUND = TextAttributesKey.createTextAttributesKey("JSP_SCRIPTING_BACKGROUND");
  public static final TextAttributesKey JSP_ACTION_AND_DIRECTIVE_BACKGROUND = TextAttributesKey.createTextAttributesKey("JSP_DIRECTIVE_BACKGROUND");
  public static final TextAttributesKey JSP_ACTION_AND_DIRECTIVE_NAME = TextAttributesKey.createTextAttributesKey("JSP_DIRECTIVE_NAME");
  public static final TextAttributesKey JSP_ATTRIBUTE_NAME = TextAttributesKey.createTextAttributesKey("JSP_ATTRIBUTE_NAME");
  public static final TextAttributesKey JSP_ATTRIBUTE_VALUE = TextAttributesKey.createTextAttributesKey("JSP_ATTRIBUTE_VALUE");
}
