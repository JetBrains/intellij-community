package com.intellij.database.diff;

import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

public interface TableDiffColors {
  TextAttributesKey TDIFF_INSERTED = DiffColors.DIFF_INSERTED;
  TextAttributesKey TDIFF_DELETED = DiffColors.DIFF_DELETED;
  TextAttributesKey TDIFF_MODIFIED = DiffColors.DIFF_MODIFIED;
  TextAttributesKey TDIFF_EXCLUDED_COLUMN = TextAttributesKey.createTextAttributesKey("TDIFF_EXCLUDED_COLUMN", EditorColors.INJECTED_LANGUAGE_FRAGMENT);
  TextAttributesKey TDIFF_FUZZY_MATCHED = TextAttributesKey.createTextAttributesKey("TDIFF_FUZZY_MATCHED", EditorColors.FOLDED_TEXT_ATTRIBUTES);
  TextAttributesKey TDIFF_FUZZY_MISMATCHED = TextAttributesKey.createTextAttributesKey("TDIFF_FUZZY_MISMATCHED", EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES);
}
