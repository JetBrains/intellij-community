/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.editor.colors;

public interface EditorColors {
  ColorKey CARET_ROW_COLOR = ColorKey.createColorKey("CARET_ROW_COLOR");
  ColorKey CARET_COLOR = ColorKey.createColorKey("CARET_COLOR");
  ColorKey RIGHT_MARGIN_COLOR = ColorKey.createColorKey("RIGHT_MARGIN_COLOR");
  ColorKey LINE_NUMBERS_COLOR = ColorKey.createColorKey("LINE_NUMBERS_COLOR");
  ColorKey ANNOTATIONS_COLOR = ColorKey.createColorKey("ANNOTATIONS_COLOR");
  ColorKey ANNOTATIONS_MERGED_COLOR = ColorKey.createColorKey("ANNOTATIONS_MERGED_COLOR");
  ColorKey READONLY_BACKGROUND_COLOR = ColorKey.createColorKey("READONLY_BACKGROUND");
  ColorKey READONLY_FRAGMENT_BACKGROUND_COLOR = ColorKey.createColorKey("READONLY_FRAGMENT_BACKGROUND");
  ColorKey WHITESPACES_COLOR = ColorKey.createColorKey("WHITESPACES");
  ColorKey INDENT_GUIDE_COLOR = ColorKey.createColorKey("INDENT_GUIDE");
  ColorKey SOFT_WRAP_SIGN_COLOR = ColorKey.createColorKey("SOFT_WRAP_SIGN_COLOR");
  ColorKey SELECTED_INDENT_GUIDE_COLOR = ColorKey.createColorKey("SELECTED_INDENT_GUIDE");
  ColorKey SELECTION_BACKGROUND_COLOR = ColorKey.createColorKey("SELECTION_BACKGROUND");
  ColorKey SELECTION_FOREGROUND_COLOR = ColorKey.createColorKey("SELECTION_FOREGROUND");

  TextAttributesKey SEARCH_RESULT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("SEARCH_RESULT_ATTRIBUTES");
  TextAttributesKey WRITE_SEARCH_RESULT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("WRITE_SEARCH_RESULT_ATTRIBUTES");
  TextAttributesKey IDENTIFIER_UNDER_CARET_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("IDENTIFIER_UNDER_CARET_ATTRIBUTES");
  TextAttributesKey WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES =
    TextAttributesKey.createTextAttributesKey("WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES");
  TextAttributesKey TEXT_SEARCH_RESULT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("TEXT_SEARCH_RESULT_ATTRIBUTES");

  TextAttributesKey FOLDED_TEXT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("FOLDED_TEXT_ATTRIBUTES");

  ColorKey GUTTER_BACKGROUND = ColorKey.createColorKey("GUTTER_BACKGROUND");
  @Deprecated ColorKey LEFT_GUTTER_BACKGROUND = GUTTER_BACKGROUND;
  ColorKey NOTIFICATION_BACKGROUND = ColorKey.createColorKey("NOTIFICATION_BACKGROUND");

  ColorKey TEARLINE_COLOR = ColorKey.createColorKey("TEARLINE_COLOR");
  ColorKey SELECTED_TEARLINE_COLOR = ColorKey.createColorKey("SELECTED_TEARLINE_COLOR");
  @Deprecated ColorKey FOLDING_TREE_COLOR = TEARLINE_COLOR;
  @Deprecated ColorKey SELECTED_FOLDING_TREE_COLOR = SELECTED_TEARLINE_COLOR;

  ColorKey ADDED_LINES_COLOR = ColorKey.createColorKey("ADDED_LINES_COLOR");
  ColorKey MODIFIED_LINES_COLOR = ColorKey.createColorKey("MODIFIED_LINES_COLOR");
  TextAttributesKey INJECTED_LANGUAGE_FRAGMENT = TextAttributesKey.createTextAttributesKey("INJECTED_LANGUAGE_FRAGMENT");
}
