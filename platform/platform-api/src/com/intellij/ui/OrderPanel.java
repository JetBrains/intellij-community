/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class OrderPanel<T> extends JPanel {
  private String CHECKBOX_COLUMN_NAME;

  private final Class<T> myEntryClass;
  private final JTable myEntryTable;

  private final List<OrderPanelListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private boolean myEntryEditable = false;

  protected OrderPanel(Class<T> entryClass) {
    this(entryClass, true);
  }

  protected OrderPanel(Class<T> entryClass, boolean showCheckboxes) {
    super(new BorderLayout());

    myEntryClass = entryClass;

    myEntryTable = new JBTable(new MyTableModel(showCheckboxes));
    myEntryTable.setShowGrid(false);
    myEntryTable.setDragEnabled(false);
    myEntryTable.setShowHorizontalLines(false);
    myEntryTable.setShowVerticalLines(false);
    myEntryTable.setIntercellSpacing(new Dimension(0, 0));
    myEntryTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    myEntryTable.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (getCheckboxColumn() == -1) return;

          int[] selectedRows = myEntryTable.getSelectedRows();
          boolean currentlyMarked = true;
          for (int selectedRow : selectedRows) {
            if (selectedRow < 0 || !myEntryTable.isCellEditable(selectedRow, getCheckboxColumn())) {
              return;
            }
            currentlyMarked &= ((Boolean)myEntryTable.getValueAt(selectedRow, getCheckboxColumn())).booleanValue();
          }
          for (int selectedRow : selectedRows) {
            myEntryTable.setValueAt(currentlyMarked ? Boolean.FALSE : Boolean.TRUE, selectedRow, getCheckboxColumn());
          }
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
      JComponent.WHEN_FOCUSED
    );

    add(ScrollPaneFactory.createScrollPane(myEntryTable), BorderLayout.CENTER);

    if (myEntryTable.getRowCount() > 0) {
      myEntryTable.getSelectionModel().setSelectionInterval(0, 0);
    }
  }

  public void setEntriesEditable(boolean entryEditable) {
    myEntryEditable = entryEditable;
  }

  public void setCheckboxColumnName(final String name) {
    TableColumn checkboxColumn = myEntryTable.getColumnModel().getColumn(getCheckboxColumn());
    if (StringUtil.isEmpty(name)) {
      CHECKBOX_COLUMN_NAME = "";
      TableUtil.setupCheckboxColumn(checkboxColumn);
    }
    else {
      CHECKBOX_COLUMN_NAME = name;
      final FontMetrics fontMetrics = myEntryTable.getFontMetrics(myEntryTable.getFont());
      final int width = fontMetrics.stringWidth(" " + name + " ") + 4;
      checkboxColumn.setWidth(width);
      checkboxColumn.setPreferredWidth(width);
      checkboxColumn.setMaxWidth(width);
      checkboxColumn.setMinWidth(width);
    }
  }

  public void moveSelectedItemsUp() {
    myEntryTable.requestFocus();
    try {
      myInsideMove++;
      TableUtil.moveSelectedItemsUp(myEntryTable);
    }
    finally {
      myInsideMove--;
    }
    for (OrderPanelListener orderPanelListener : myListeners) {
      orderPanelListener.entryMoved();
    }
  }

  public void moveSelectedItemsDown() {
    myEntryTable.requestFocus();
    try {
      myInsideMove++;
      TableUtil.moveSelectedItemsDown(myEntryTable);
    }
    finally {
      myInsideMove--;
    }
    for (OrderPanelListener orderPanelListener : myListeners) {
      orderPanelListener.entryMoved();
    }
  }

  private int myInsideMove = 0;

  private boolean isInsideMove() {
    return myInsideMove != 0;
  }

  public void addListener(OrderPanelListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(OrderPanelListener listener) {
    myListeners.remove(listener);
  }

  public JTable getEntryTable() {
    return myEntryTable;
  }

  public void clear() {
    MyTableModel model = getModel();
    while (model.getRowCount() > 0) {
      model.removeRow(0);
    }
  }

  public void remove(T orderEntry) {
    MyTableModel model = getModel();
    int rowCount = model.getRowCount();
    for (int i = 0; i < rowCount; i++) {
      if (getValueAt(i) == orderEntry) {
        model.removeRow(i);
        return;
      }
    }
  }

  public void add(T orderEntry) {
    MyTableModel model = getModel();
    if (getCheckboxColumn() == -1) {
      model.addRow(new Object[]{orderEntry});
    }
    else {
      model.addRow(new Object[]{isChecked(orderEntry) ? Boolean.TRUE : Boolean.FALSE, orderEntry});
    }
  }

  public void addAll(Collection<T> orderEntries) {
    for (T orderEntry : orderEntries) {
      add(orderEntry);
    }
  }

  protected int getEntryColumn() {
    return getModel().getEntryColumn();
  }

  private int getCheckboxColumn() {
    return getModel().getCheckboxColumn();
  }

  private MyTableModel getModel() {
    @SuppressWarnings("unchecked") MyTableModel model = (MyTableModel)myEntryTable.getModel();
    return model;
  }

  private class MyTableModel extends DefaultTableModel {
    private final boolean myShowCheckboxes;

    public MyTableModel(boolean showCheckboxes) {
      myShowCheckboxes = showCheckboxes;
    }

    private int getEntryColumn() {
      return getColumnCount() - 1;
    }

    private int getCheckboxColumn() {
      return getColumnCount() - 2;
    }

    public String getColumnName(int column) {
      if (column == getEntryColumn()) {
        return "";
      }
      if (column == getCheckboxColumn()) {
        return getCheckboxColumnName();
      }
      return null;
    }

    public Class getColumnClass(int column) {
      if (column == getEntryColumn()) {
        return myEntryClass;
      }
      if (column == getCheckboxColumn()) {
        return Boolean.class;
      }
      return super.getColumnClass(column);
    }

    public int getColumnCount() {
      return myShowCheckboxes ? 2 : 1;
    }

    public boolean isCellEditable(int row, int column) {
      if (column == getCheckboxColumn()) {
        return isCheckable(OrderPanel.this.getValueAt(row));
      }
      return myEntryEditable;
    }

    public void setValueAt(Object aValue, int row, int column) {
      super.setValueAt(aValue, row, column);
      if (!isInsideMove() && column == getCheckboxColumn()) {
        setChecked(OrderPanel.this.getValueAt(row), ((Boolean)aValue).booleanValue());
      }
    }
  }

  public T getValueAt(int row) {
    @SuppressWarnings("unchecked") T t = (T)myEntryTable.getModel().getValueAt(row, getEntryColumn());
    return t;
  }

  public abstract boolean isCheckable(T entry);

  public abstract boolean isChecked(T entry);

  public abstract void setChecked(T entry, boolean checked);

  public String getCheckboxColumnName() {
    if (CHECKBOX_COLUMN_NAME == null) {
      CHECKBOX_COLUMN_NAME = UIBundle.message("order.entries.panel.export.column.name");
    }
    return CHECKBOX_COLUMN_NAME;
  }

  public List<T> getEntries() {
    final TableModel model = myEntryTable.getModel();
    final int size = model.getRowCount();
    List<T> result = new ArrayList<>(size);
    for (int idx = 0; idx < size; idx++) {
      result.add(getValueAt(idx));
    }

    return result;
  }
}
