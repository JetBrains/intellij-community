/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.debugger;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ColorPicker;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.Enumeration;
import java.util.EventObject;

/**
 * @author Konstantin Bulenkov
 */
public class ShowUIDefaultsAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final UIDefaults defaults = UIManager.getDefaults();
    Enumeration keys = defaults.keys();
    final Object[][] data = new Object[defaults.size()][2];
    int i = 0;
    while (keys.hasMoreElements()) {
      Object key = keys.nextElement();
      data[i][0] = key;
      data[i][1] = defaults.get(key);
      i++;
    }

    new DialogWrapper(getEventProject(e)) {
      {
        setTitle("Edit LaF Defaults");
        init();
      }

      @Override
      protected JComponent createCenterPanel() {
        final JBTable table = new JBTable(new DefaultTableModel(data, new Object[]{"Name", "Value"}) {
          @Override
          public boolean isCellEditable(int row, int column) {
            return column == 1 && getValueAt(row, column) instanceof Color;
          }
        }) {
          @Override
          public boolean editCellAt(int row, int column, EventObject e) {
            if (isCellEditable(row, column)) {
              final Object color = getValueAt(row, column);
              final Color newColor = ColorPicker.showDialog(this, "Choose Color", (Color)color, true, null);
              if (newColor != null) {
                final ColorUIResource colorUIResource = new ColorUIResource(newColor);
                final Object key = getValueAt(row, 0);
                UIManager.put(key, colorUIResource);
                setValueAt(colorUIResource, row, column);
              }
            }
            return false;
          }
        };
        table.setDefaultRenderer(Object.class, new TableCellRenderer() {
          @Override
          public Component getTableCellRendererComponent(JTable table,
                                                         Object value,
                                                         boolean isSelected,
                                                         boolean hasFocus,
                                                         int row,
                                                         int column) {
            final JLabel label = new JLabel(value == null ? "" : value.toString());
            if (value instanceof Color) {
              final JPanel panel = new JPanel(new BorderLayout());
              panel.setBackground((Color)value);
              panel.add(label, BorderLayout.CENTER);
              return panel;
            }
            return label;
          }
        });
        final JBScrollPane pane = new JBScrollPane(table);
        new TableSpeedSearch(table);
        table.setShowGrid(false);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
          @Override
          public Component getTableCellRendererComponent(JTable table,
                                                         Object value,
                                                         boolean isSelected,
                                                         boolean hasFocus,
                                                         int row,
                                                         int column) {
            if (value instanceof Color) {
              final JPanel panel = new JPanel();
              panel.setBackground((Color)value);
              return panel;
            }
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          }
        });
        final JPanel panel = new JPanel(new BorderLayout());
        panel.add(pane, BorderLayout.CENTER);
        return panel;
      }
    }.show();
  }
}
