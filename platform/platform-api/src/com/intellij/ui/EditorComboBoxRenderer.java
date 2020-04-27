// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import java.awt.*;

public class EditorComboBoxRenderer extends BasicComboBoxRenderer {
  private final ComboBoxEditor myEditor;

  public EditorComboBoxRenderer(ComboBoxEditor editor) {
    myEditor = editor;
  }

  @Override
  public Component getListCellRendererComponent(JList list,
                                                Object value,
                                                int index,
                                                boolean isSelected,
                                                boolean cellHasFocus) {
    final Component editorComponent = myEditor.getEditorComponent();
    Font editorFont = editorComponent.getFont();
    final Component component = super.getListCellRendererComponent(list, UIUtil.htmlInjectionGuard(value), index, isSelected, cellHasFocus);
    component.setFont(editorFont);
    component.setSize(editorComponent.getSize());
    return component;
  }
}
