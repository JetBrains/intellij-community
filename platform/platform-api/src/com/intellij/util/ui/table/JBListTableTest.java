/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.util.ui.table;

import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class JBListTableTest extends JFrame {
  public JBListTableTest() throws HeadlessException {
    super("Test");
    final JBTable t = new JBTable(new DefaultTableModel(new Object[][]{
      {"sdfasdfdsfds", "werqwefsdfasdfasd"},
      {"sdfasdfdsfds", "werqwefsdfasdfasd"},
      {"sdfasdfdsfds", "werqwefsdfasdfasd"},
      {"sdfasdfdsfds", "werqwefsdfasdfasd"},
      {"sdfasdfdsfds", "werqwefsdfasdfasd"},
    }, new Object[]{"Name", "Value"}));
    final JBListTable table = new JBListTable(t) {
      @Override
      protected JComponent getRowRenderer(JTable table, int row, boolean selected, boolean focused) {
        final SimpleColoredComponent component = new SimpleColoredComponent();
        final JBTableRow r = getRowAt(row);
        component.append("Name: ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        component.append(r.getValueAt(0).toString());
        component.append(" Value: ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        component.append(r.getValueAt(1).toString());
        component.setBackground(selected ? table.getSelectionBackground() : table.getBackground());
        component.setForeground(selected ? table.getSelectionForeground() : table.getForeground());
        return component;
      }

      @Override
      protected JBTableRowEditor getRowEditor(int row) {
        return new JBTableRowEditor() {
          JTextField name = new JTextField();
          JTextField value = new JTextField();
          JCheckBox checkbox = new JCheckBox("Check me");

          @Override
          public void prepareEditor(JTable table, int row) {
            final JBTableRow r = getRowAt(row);
            setLayout(new VerticalFlowLayout());
            final JPanel top = new JPanel(new BorderLayout());
            top.add(new JLabel("Name:"), BorderLayout.WEST);
            name.setText(r.getValueAt(0).toString());
            top.add(name, BorderLayout.CENTER);
            final JPanel bottom = new JPanel(new BorderLayout());
            bottom.add(new JLabel("Name:"), BorderLayout.WEST);
            value.setText(r.getValueAt(0).toString());
            bottom.add(value, BorderLayout.CENTER);

            add(top);
            add(bottom);
            add(checkbox);
          }

          @Override
          public JBTableRow getValue() {
            return new JBTableRow() {
              @Override
              public Object getValueAt(int column) {
                return column == 0 ? name.getText() : value.getText();
              }
            };
          }

          @Override
          public JComponent getPreferredFocusedComponent() {
            return name;
          }

          @Override
          public JComponent[] getFocusableComponents() {
            return new JComponent[]{name, value, checkbox};
          }
        };
      }

      @Override
      protected JBTableRow getRowAt(final int row) {
        return new JBTableRow() {
          @Override
          public Object getValueAt(int column) {
            return t.getValueAt(row, column);
          }
        };
      }
    };
    getContentPane().add(table);
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setSize(300, 300);
    setVisible(true);
  }

  public static void main(String[] args) {
    new JBListTableTest();
  }
}
