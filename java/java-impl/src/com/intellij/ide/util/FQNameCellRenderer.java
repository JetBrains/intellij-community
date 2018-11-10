// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.psi.PsiClass;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import java.awt.*;

public class FQNameCellRenderer extends SimpleColoredComponent implements ListCellRenderer{
  private final Font FONT;
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.FQNameCellRenderer");

  public FQNameCellRenderer() {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    FONT = new Font(scheme.getEditorFontName(), Font.PLAIN, scheme.getEditorFontSize());
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

    if (value instanceof PsiClass) {
      PsiClass aClass = (PsiClass)value;
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
