// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;

import java.awt.*;

public interface EditorColors {
  ColorKey CARET_ROW_COLOR = ColorKey.createColorKey("CARET_ROW_COLOR");
  ColorKey CARET_COLOR = ColorKey.createColorKey("CARET_COLOR");
  ColorKey RIGHT_MARGIN_COLOR = ColorKey.createColorKey("RIGHT_MARGIN_COLOR");
  ColorKey LINE_NUMBERS_COLOR = ColorKey.createColorKey("LINE_NUMBERS_COLOR");
  ColorKey ANNOTATIONS_COLOR = ColorKey.createColorKey("ANNOTATIONS_COLOR");
  ColorKey READONLY_BACKGROUND_COLOR = ColorKey.createColorKey("READONLY_BACKGROUND");
  ColorKey READONLY_FRAGMENT_BACKGROUND_COLOR = ColorKey.createColorKey("READONLY_FRAGMENT_BACKGROUND");
  ColorKey WHITESPACES_COLOR = ColorKey.createColorKey("WHITESPACES");
  ColorKey INDENT_GUIDE_COLOR = ColorKey.createColorKey("INDENT_GUIDE");
  ColorKey SOFT_WRAP_SIGN_COLOR = ColorKey.createColorKey("SOFT_WRAP_SIGN_COLOR");
  ColorKey SELECTED_INDENT_GUIDE_COLOR = ColorKey.createColorKey("SELECTED_INDENT_GUIDE");
  ColorKey SELECTION_BACKGROUND_COLOR = ColorKey.createColorKey("SELECTION_BACKGROUND");
  ColorKey SELECTION_FOREGROUND_COLOR = ColorKey.createColorKey("SELECTION_FOREGROUND");

  TextAttributesKey REFERENCE_HYPERLINK_COLOR = TextAttributesKey.createTextAttributesKey("CTRL_CLICKABLE", new TextAttributes(JBColor.blue, null, JBColor.blue, EffectType.LINE_UNDERSCORE,
                                                                                                                               Font.PLAIN));

  TextAttributesKey SEARCH_RESULT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("SEARCH_RESULT_ATTRIBUTES");
  TextAttributesKey LIVE_TEMPLATE_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("LIVE_TEMPLATE_ATTRIBUTES");
  TextAttributesKey WRITE_SEARCH_RESULT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("WRITE_SEARCH_RESULT_ATTRIBUTES");
  TextAttributesKey IDENTIFIER_UNDER_CARET_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("IDENTIFIER_UNDER_CARET_ATTRIBUTES");
  TextAttributesKey WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES =
    TextAttributesKey.createTextAttributesKey("WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES");
  TextAttributesKey TEXT_SEARCH_RESULT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("TEXT_SEARCH_RESULT_ATTRIBUTES");

  TextAttributesKey FOLDED_TEXT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("FOLDED_TEXT_ATTRIBUTES");
  ColorKey FOLDED_TEXT_BORDER_COLOR = ColorKey.createColorKey("FOLDED_TEXT_BORDER_COLOR");
  TextAttributesKey DELETED_TEXT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("DELETED_TEXT_ATTRIBUTES");

  ColorKey GUTTER_BACKGROUND = ColorKey.createColorKey("GUTTER_BACKGROUND", new JBColor(0xf0f0f0, 0x313335));
  @Deprecated ColorKey LEFT_GUTTER_BACKGROUND = GUTTER_BACKGROUND;
  ColorKey NOTIFICATION_BACKGROUND = ColorKey.createColorKey("NOTIFICATION_BACKGROUND");

  ColorKey TEARLINE_COLOR = ColorKey.createColorKey("TEARLINE_COLOR");
  ColorKey SELECTED_TEARLINE_COLOR = ColorKey.createColorKey("SELECTED_TEARLINE_COLOR");
  ColorKey SEPARATOR_ABOVE_COLOR = ColorKey.createColorKey("SEPARATOR_ABOVE_COLOR");
  ColorKey SEPARATOR_BELOW_COLOR = ColorKey.createColorKey("SEPARATOR_BELOW_COLOR");

  ColorKey ADDED_LINES_COLOR = ColorKey.createColorKey("ADDED_LINES_COLOR");
  ColorKey MODIFIED_LINES_COLOR = ColorKey.createColorKey("MODIFIED_LINES_COLOR");
  ColorKey DELETED_LINES_COLOR = ColorKey.createColorKey("DELETED_LINES_COLOR");
  ColorKey WHITESPACES_MODIFIED_LINES_COLOR = ColorKey.createColorKey("WHITESPACES_MODIFIED_LINES_COLOR");
  ColorKey BORDER_LINES_COLOR = ColorKey.createColorKey("BORDER_LINES_COLOR");
  ColorKey IGNORED_ADDED_LINES_BORDER_COLOR = ColorKey.createColorKey("IGNORED_ADDED_LINES_BORDER_COLOR");
  ColorKey IGNORED_MODIFIED_LINES_BORDER_COLOR = ColorKey.createColorKey("IGNORED_MODIFIED_LINES_BORDER_COLOR");
  ColorKey IGNORED_DELETED_LINES_BORDER_COLOR = ColorKey.createColorKey("IGNORED_DELETED_LINES_BORDER_COLOR");

  TextAttributesKey INJECTED_LANGUAGE_FRAGMENT = TextAttributesKey.createTextAttributesKey("INJECTED_LANGUAGE_FRAGMENT");
  
  TextAttributesKey BREADCRUMBS_DEFAULT  = TextAttributesKey.createTextAttributesKey("BREADCRUMBS_DEFAULT");
  TextAttributesKey BREADCRUMBS_HOVERED  = TextAttributesKey.createTextAttributesKey("BREADCRUMBS_HOVERED");
  TextAttributesKey BREADCRUMBS_CURRENT  = TextAttributesKey.createTextAttributesKey("BREADCRUMBS_CURRENT");
  TextAttributesKey BREADCRUMBS_INACTIVE = TextAttributesKey.createTextAttributesKey("BREADCRUMBS_INACTIVE");

  ColorKey VISUAL_INDENT_GUIDE_COLOR = ColorKey.createColorKey("VISUAL_INDENT_GUIDE");
}
