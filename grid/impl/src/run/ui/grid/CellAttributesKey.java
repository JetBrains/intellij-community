package com.intellij.database.run.ui.grid;

import com.intellij.openapi.editor.colors.TextAttributesKey;

public class CellAttributesKey {
  public CellAttributesKey(TextAttributesKey attributes, boolean isUnderlined) {
    this.attributes = attributes;
    this.isUnderlined = isUnderlined;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CellAttributesKey key = (CellAttributesKey)o;

    if (isUnderlined != key.isUnderlined) return false;
    if (attributes != null ? !attributes.equals(key.attributes) : key.attributes != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = attributes != null ? attributes.hashCode() : 0;
    result = 31 * result + (isUnderlined ? 1 : 0);
    return result;
  }

  public TextAttributesKey attributes;
  public boolean isUnderlined;
}
