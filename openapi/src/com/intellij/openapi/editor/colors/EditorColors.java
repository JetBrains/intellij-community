/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.colors;

public interface EditorColors {
  ColorKey CARET_ROW_COLOR = ColorKey.createColorKey("CARET_ROW_COLOR");
  ColorKey CARET_COLOR = ColorKey.createColorKey("CARET_COLOR");
  ColorKey RIGHT_MARGIN_COLOR = ColorKey.createColorKey("RIGHT_MARGIN_COLOR");
  ColorKey LINE_NUMBERS_COLOR = ColorKey.createColorKey("LINE_NUMBERS_COLOR");
  ColorKey ANNOTATIONS_COLOR = ColorKey.createColorKey("ANNOTATIONS_COLOR");
  ColorKey BACKGROUND_COLOR = ColorKey.createColorKey("BACKGROUND");
  ColorKey READONLY_BACKGROUND_COLOR = ColorKey.createColorKey("READONLY_BACKGROUND");
  ColorKey READONLY_FRAGMENT_BACKGROUND_COLOR = ColorKey.createColorKey("READONLY_FRAGMENT_BACKGROUND");
  ColorKey WHITESPACES_COLOR = ColorKey.createColorKey("WHITESPACES");
  ColorKey SELECTION_BACKGROUND_COLOR = ColorKey.createColorKey("SELECTION_BACKGROUND");
  ColorKey SELECTION_FOREGROUND_COLOR = ColorKey.createColorKey("SELECTION_FOREGROUND");

  TextAttributesKey SEARCH_RESULT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("SEARCH_RESULT_ATTRIBUTES");
  TextAttributesKey WRITE_SEARCH_RESULT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("WRITE_SEARCH_RESULT_ATTRIBUTES");
  TextAttributesKey TEXT_SEARCH_RESULT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("TEXT_SEARCH_RESULT_ATTRIBUTES");

  TextAttributesKey FOLDED_TEXT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("FOLDED_TEXT_ATTRIBUTES");
  ColorKey FOLDING_TREE_COLOR = ColorKey.createColorKey("FOLDING_TREE_COLOR");
  ColorKey SELECTED_FOLDING_TREE_COLOR = ColorKey.createColorKey("SELECTED_FOLDING_TREE_COLOR");

  ColorKey ADDED_LINES_COLOR = ColorKey.createColorKey("ADDED_LINES_COLOR");
  ColorKey MODIFIED_LINES_COLOR = ColorKey.createColorKey("MODIFIED_LINES_COLOR");
}
