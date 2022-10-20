// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.ui.ComboBox;

import java.awt.Component;
import java.util.List;
import java.util.Objects;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;

public abstract class ComboBoxCellEditor extends DefaultCellEditor {
  public ComboBoxCellEditor() {
    super(new ComboBox());
    setClickCountToStart(2);
  }

  protected abstract List<String> getComboBoxItems();

  protected boolean isComboboxEditable() {
    return false;
  }

  @Override
  public boolean stopCellEditing() {
    final JComboBox comboBox = (JComboBox)editorComponent;
    comboBox.removeActionListener(delegate);
    final boolean result = super.stopCellEditing();
    comboBox.addActionListener(delegate);
    return result;
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    String currentValue = (String)value;
    final JComboBox component = (JComboBox)super.getTableCellEditorComponent(table, value, isSelected, row, column);
    component.removeActionListener(delegate);
    component.setBorder(null);
    component.removeAllItems();
    final List<String> items = getComboBoxItems();
    int selected = -1;
    for (int i = 0; i < items.size(); i++) {
      final String item = items.get(i);
      component.addItem(item);
      if (Objects.equals(item, currentValue)) {
        selected = i;
      }
    }
    if (selected == -1) {
      component.setEditable(true);
      component.setSelectedItem(currentValue);
      component.setEditable(false);
    } else {
      component.setSelectedIndex(selected);
    }
    component.setEditable(isComboboxEditable());
    component.addActionListener(delegate);
    return component;
  }
}
