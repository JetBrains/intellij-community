package com.intellij.openapi.diff;

import com.intellij.openapi.editor.colors.TextAttributesKey;

public interface DiffColors {
  TextAttributesKey DIFF_ABSENT = TextAttributesKey.createTextAttributesKey("DIFF_ABSENT");
  TextAttributesKey DIFF_MODIFIED = TextAttributesKey.createTextAttributesKey("DIFF_MODIFIED");
  TextAttributesKey DIFF_DELETED = TextAttributesKey.createTextAttributesKey("DIFF_DELETED");
  TextAttributesKey DIFF_INSERTED = TextAttributesKey.createTextAttributesKey("DIFF_INSERTED");
  TextAttributesKey DIFF_CONFLICT = TextAttributesKey.createTextAttributesKey("DIFF_CONFLICT");
}
