package com.intellij.database.editor;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public final class DataGridColors {
  public static final TextAttributesKey GRID_NULL_VALUE = createTextAttributesKey("GRID_NULL_VALUE", DefaultLanguageHighlighterColors.LINE_COMMENT);
  public static final TextAttributesKey GRID_IMAGE_VALUE = createTextAttributesKey("GRID_IMAGE_VALUE", EditorColors.FOLDED_TEXT_ATTRIBUTES);
  public static final TextAttributesKey GRID_ERROR_VALUE = createTextAttributesKey("GRID_ERROR_VALUE", CodeInsightColors.ERRORS_ATTRIBUTES);
  public static final TextAttributesKey GRID_UPLOAD_VALUE = createTextAttributesKey("GRID_UPLOAD_VALUE", DefaultLanguageHighlighterColors.METADATA);
  public static final TextAttributesKey GRID_RESULT_VALUE = createTextAttributesKey("GRID_RESULT_VALUE", EditorColors.FOLDED_TEXT_ATTRIBUTES);
  public static final ColorKey GRID_SELECTION_BACKGROUND = EditorColors.SELECTION_BACKGROUND_COLOR;
  public static final ColorKey GRID_SELECTION_FOREGROUND = EditorColors.SELECTION_FOREGROUND_COLOR;
  public static final ColorKey GRID_CURRENT_ROW = EditorColors.CARET_ROW_COLOR;
  public static final ColorKey GRID_STRIPE_COLOR = ColorKey.createColorKeyWithFallback("GRID_STRIPE_COLOR", null);
  public static final TextAttributesKey STRUCTURE_HIDDEN_COLUMN = createTextAttributesKey("STRUCTURE_HIDDEN_COLUMN", CodeInsightColors.DEPRECATED_ATTRIBUTES);
}
