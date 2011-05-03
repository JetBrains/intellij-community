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
package com.intellij.ui.components.editors;

import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.Function;
import com.intellij.util.Icons;
import com.intellij.util.ui.EmptyIcon;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

/**
 * Solves rendering problems in JTable components when JComboBox objects are used as cell
 * editors components. Known issues of using JComboBox component are the following:
 *   1. Ugly view if row height is small enough
 *   2. Truncated strings in the combobox popup if column width is less than text value width
 *
 * @author Konstantin Bulenkov
 */
public class JBComboBoxTableCellEditorComponent extends JBLabel {
  private JTable myTable;
  private int myRow = 0;
  private int myColumn = 0;
  private final JBList myList = new JBList();
  private Object[] myOptions = {};
  private Object myValue;
  private Function<Object, String> myToString = StringUtil.createToStringFunction(Object.class);
  private ArrayList<ActionListener> myListeners = new ArrayList<ActionListener>();

  @SuppressWarnings({"GtkPreferredJComboBoxRenderer"})
  private ListCellRenderer myRenderer = new DefaultListCellRenderer() {
    public Icon myEmptyIcon;

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      final JLabel label = (JLabel)super.getListCellRendererComponent(list, myToString.fun(value), index, isSelected, cellHasFocus);
      if (value == myValue) {
        label.setIcon(getIcon(isSelected));
      } else {
        label.setIcon(getEmptyIcon());
      }
      return label;
    }

    private Icon getEmptyIcon() {
      if (myEmptyIcon == null) {
        myEmptyIcon = EmptyIcon.create(getIcon(true).getIconWidth());
      }
      return myEmptyIcon;
    }

    private Icon getIcon(boolean selected) {
      final boolean small = "small".equals(JBComboBoxTableCellEditorComponent.this.getClientProperty("JComponent.sizeVariant"));
      return small
             ? selected ? Icons.CHECK_ICON_SMALL_SELECTED : Icons.CHECK_ICON_SMALL
             : selected ? Icons.CHECK_ICON_SELECTED : Icons.CHECK_ICON;
    }
  };

  public JBComboBoxTableCellEditorComponent() {
  }

  public JBComboBoxTableCellEditorComponent(JTable table) {
    myTable = table;
  }

  public void setCell(JTable table, int row, int column) {
    setTable(table);
    setRow(row);
    setColumn(column);
  }

  public void setTable(JTable table) {
    myTable = table;
  }

  public void setRow(int row) {
    myRow = row;
  }

  public void setColumn(int column) {
    myColumn = column;
  }

  public Object[] getOptions() {
    return myOptions;
  }

  public void setOptions(Object... options) {
    myOptions = options;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    initAndShowPopup();
  }

  private void initAndShowPopup() {
    myList.removeAll();
    final Rectangle rect = myTable.getCellRect(myRow, myColumn, true);
    final Point point = new Point(rect.x, rect.y);
    myList.setModel(JBList.createDefaultListModel(myOptions));
    if (myRenderer != null) {
      myList.setCellRenderer(myRenderer);
    }
    JBPopupFactory.getInstance()
      .createListPopupBuilder(myList)
      .setItemChoosenCallback(new Runnable() {
        @Override
        public void run() {
          final ActionEvent event = new ActionEvent(myList, ActionEvent.ACTION_PERFORMED, "elementChosen");
          for (ActionListener listener : myListeners) {
            listener.actionPerformed(event);
          }
          //myValue = myList.getSelectedValue();
          //myTable.setValueAt(myValue, myRow, myColumn); // on Mac getCellEditorValue() called before myValue is set.
          //myTable.tableChanged(new TableModelEvent(myTable.getModel(), myRow));
        }
      }).createPopup()
      .show(new RelativePoint(myTable, point));
  }

  public Object getEditorValue() {
    return myList.getSelectedValue();
  }

  public void setRenderer(ListCellRenderer renderer) {
    myRenderer = renderer;
  }

  public void setDefaultValue(Object value) {
    myValue = value;
  }

  public void setToString(Function<Object, String> toString) {
    myToString = toString;
  }

  public void addActionListener(ActionListener listener) {
    myListeners.add(listener);
  }
}
