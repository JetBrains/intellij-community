// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.psi.PsiClass;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import java.awt.*;

public class FQNameCellRenderer extends SimpleColoredComponent implements ListCellRenderer<Object> {
  private final Font FONT;
  private static final Logger LOG = Logger.getInstance(FQNameCellRenderer.class);

  public FQNameCellRenderer() {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    FONT = scheme.getFont(EditorFontType.PLAIN);
    setOpaque(true);
  }

  @Override
  public Component getListCellRendererComponent(
    JList list,
    Object value,
    int index,
    boolean isSelected,
    boolean cellHasFocus){

    clear();

    if (value instanceof PsiClass aClass) {
      setIcon(aClass.getIcon(0));
      if (aClass.getQualifiedName() != null) {
        SimpleTextAttributes attributes;
        if (aClass.isDeprecated()) {
          attributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, null);
        }
        else {
          attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
        }
        append(aClass.getQualifiedName(), attributes);
      }
    }
    else {
      LOG.assertTrue(value instanceof String);
      String qName = (String)value;
      append(qName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      setIcon(AllIcons.Nodes.Static);
    }

    setFont(FONT);
    if (isSelected) {
      setBackground(list.getSelectionBackground());
      setForeground(list.getSelectionForeground());
    }
    else {
      setBackground(list.getBackground());
      setForeground(list.getForeground());
    }
    return this;
  }
}
