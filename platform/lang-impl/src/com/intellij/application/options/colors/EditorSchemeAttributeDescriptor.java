package com.intellij.application.options.colors;

import com.intellij.openapi.editor.colors.EditorColorsScheme;

public interface EditorSchemeAttributeDescriptor {
  String getGroup();

  String getType();

  EditorColorsScheme getScheme();

  void apply(EditorColorsScheme scheme);

  boolean isModified();
}
