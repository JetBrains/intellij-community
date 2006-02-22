/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.j2ee.module.view;

import com.intellij.openapi.util.Comparing;

import javax.swing.*;
import java.util.List;
import java.awt.*;

/**
 * @author peter
 */
public abstract class ComboBoxCellEditor extends DefaultCellEditor {
  public ComboBoxCellEditor() {
    super(new JComboBox());
    setClickCountToStart(2);
  }

  protected abstract List<String> getComboBoxItems();

  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    String servletName = (String)value;
    final JComboBox component = (JComboBox)super.getTableCellEditorComponent(table, value, isSelected, row, column);
    component.removeAllItems();
    final List<String> items = getComboBoxItems();
    int selected = -1;
    for (int i = 0; i < items.size(); i++) {
      final String item = items.get(i);
      component.addItem(item);
      if (Comparing.equal(item, servletName)) {
        selected = i;
      }
    }
    if (selected == -1) {
      component.setEditable(true);
      component.setSelectedItem(servletName);
      component.setEditable(false);
    } else {
      component.setSelectedItem(selected);
    }
    return component;
  }
}
