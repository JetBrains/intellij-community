package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;

/**
 * @author Roman Chernyatchik
 */
public class ReadOnlyColorsSchemeImpl extends EditorColorsSchemeImpl implements ReadOnlyColorsScheme {
  public ReadOnlyColorsSchemeImpl(final EditorColorsScheme parenScheme,
                                  final DefaultColorSchemesManager defaultColorSchemesManager) {
    super(parenScheme, defaultColorSchemesManager);
  }
}
