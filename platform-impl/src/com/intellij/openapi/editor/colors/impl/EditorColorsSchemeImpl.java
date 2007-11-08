/**
 * @author Yura Cangea
 */
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.markup.TextAttributes;

import java.awt.*;

public class EditorColorsSchemeImpl extends AbstractColorsScheme {

  public EditorColorsSchemeImpl(EditorColorsScheme parenScheme, DefaultColorSchemesManager defaultColorSchemesManager) {
    super(parenScheme, defaultColorSchemesManager);
  }

  // -------------------------------------------------------------------------
  // Getters & Setters
  // -------------------------------------------------------------------------
  public void setAttributes(TextAttributesKey key, TextAttributes attributes) {
    myAttributesMap.put(key, attributes);
  }

  public void setColor(ColorKey key, Color color) {
    myColorsMap.put(key, color);
  }

  public TextAttributes getAttributes(TextAttributesKey key) {
    if (myAttributesMap.containsKey(key)) {
      return myAttributesMap.get(key);
    } else {
      return myParentScheme.getAttributes(key);
    }
  }

  public Color getColor(ColorKey key) {
    if (myColorsMap.containsKey(key)) {
      return myColorsMap.get(key);
    } else {
      return myParentScheme.getColor(key);
    }
  }

  public Object clone() {
    EditorColorsSchemeImpl newScheme = new EditorColorsSchemeImpl(myParentScheme, DefaultColorSchemesManager.getInstance());
    copyTo(newScheme);
    return newScheme;
  }
}
