/**
 * @author Yura Cangea
 */
/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.JDOMExternalizable;

import java.awt.*;

public interface EditorColorsScheme extends Cloneable, JDOMExternalizable {
  String getName();
  void setName(String name);

  TextAttributes getAttributes(TextAttributesKey key);
  void setAttributes(TextAttributesKey key, TextAttributes attributes);

  Color getColor(ColorKey key);
  void setColor(ColorKey key, Color color);

  int getEditorFontSize();
  void setEditorFontSize(int fontSize);

  String getEditorFontName();
  void setEditorFontName(String fontName);

  Font getFont(EditorFontType key);
  void setFont(EditorFontType key, Font font);

  float getLineSpacing();
  void setLineSpacing(float lineSpacing);

  Object clone();
}
