// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Collection;

public abstract class OrderPanel<T> extends JPanel {

  private final JBTable myEntryTable = new JBTable();
  private final @NotNull Class<T> myEntryClass;
  private final @NotNull MyTableModel myModel;
  private final @NlsContexts.ColumnName @NotNull String myCheckboxColumnName;

  protected OrderPanel(@NotNull Class<T> entryClass) {
    this(entryClass, true, "");
  }

  protected OrderPanel(@NotNull Class<T> entryClass,
                       boolean showCheckboxes,
                       @NlsContexts.ColumnName @Nullable String checkboxColumnName) {
    super(new BorderLayout());

    myEntryClass = entryClass;
    myModel = new MyTableModel(showCheckboxes);
    myCheckboxColumnName = checkboxColumnName != null ?
                           checkboxColumnName :
                           UIBundle.message("order.entries.panel.export.column.name");

    myEntryTable.setModel(myModel);
    myEntryTable.setShowGrid(false);
    myEntryTable.setDragEnabled(false);
    myEntryTable.setShowHorizontalLines(false);
    myEntryTable.setShowVerticalLines(false);
    myEntryTable.setIntercellSpacing(new Dimension(0, 0));
    myEntryTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    myEntryTable.registerKeyboardAction(
      new ActionListener() {
        @Override
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
    setCheckboxColumnWidth();

    add(ScrollPaneFactory.createScrollPane(myEntryTable), BorderLayout.CENTER);

    if (myEntryTable.getRowCount() > 0) {
      myEntryTable.getSelectionModel().setSelectionInterval(0, 0);
    }
  }

  private void setCheckboxColumnWidth() {
    TableColumnModel columnModel = myEntryTable.getColumnModel();
    TableColumn checkboxColumn = columnModel.getColumn(getCheckboxColumn());
    if (myCheckboxColumnName.isEmpty()) {
      TableUtil.setupCheckboxColumn(checkboxColumn, columnModel.getColumnMargin());
    }
    else {
      int width = myEntryTable.getFontMetrics(myEntryTable.getFont())
                    .stringWidth(" " + myCheckboxColumnName + " ") + 4;
      checkboxColumn.setWidth(width);
      checkboxColumn.setPreferredWidth(width);
      checkboxColumn.setMaxWidth(width);
      checkboxColumn.setMinWidth(width);
    }
  }

  protected final @NotNull JTable getEntryTable() {
    return myEntryTable;
  }

  public final void clear() {
    while (myModel.getRowCount() > 0) {
      myModel.removeRow(0);
    }
  }

  public final void remove(T orderEntry) {
    int rowCount = myModel.getRowCount();
    for (int i = 0; i < rowCount; i++) {
      if (myModel.getValueAt(i) == orderEntry) {
        myModel.removeRow(i);
        return;
      }
    }
  }

  public final void add(@NotNull T orderEntry) {
    Object[] row = getCheckboxColumn() == -1 ?
                   new Object[]{orderEntry} :
                   new Object[]{Boolean.valueOf(isChecked(orderEntry)), orderEntry};
    myModel.addRow(row);
  }

  public void addAll(@NotNull Collection<? extends T> orderEntries) {
    for (T orderEntry : orderEntries) {
      add(orderEntry);
    }
  }

  protected final int getEntryColumn() {
    return myModel.myEntryColumn;
  }

  protected final int getCheckboxColumn() {
    return myModel.myCheckboxColumn;
  }

  private class MyTableModel extends DefaultTableModel {

    private final int myColumnCount;
    private final int myEntryColumn;
    private final int myCheckboxColumn;

    MyTableModel(boolean showCheckboxes) {
      myColumnCount = showCheckboxes ? 2 : 1;
      myEntryColumn = myColumnCount - 1;
      myCheckboxColumn = myColumnCount - 2;
    }

    @Override
    public @Nullable String getColumnName(int column) {
      return column == myEntryColumn ?
             "" :
             column == myCheckboxColumn ?
             myCheckboxColumnName :
             null;
    }

    @Override
    public @NotNull Class<?> getColumnClass(int column) {
      return column == myEntryColumn ?
             myEntryClass :
             column == myCheckboxColumn ?
             Boolean.class :
             super.getColumnClass(column);
    }

    @Override
    public int getColumnCount() {
      return myColumnCount;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return column == myCheckboxColumn &&
             isCheckable(getValueAt(row));
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
      super.setValueAt(value, row, column);
      if (column == myCheckboxColumn) {
        setChecked(getValueAt(row), ((Boolean)value).booleanValue());
      }
    }

    @SuppressWarnings("unchecked")
    private T getValueAt(int row) {
      return (T) getValueAt(row, myEntryColumn);
    }
  }

  protected final T getValueAt(int row) {
    return myModel.getValueAt(row);
  }

  public boolean isCheckable(@SuppressWarnings("unused") @NotNull T entry) {
    return true;
  }

  public abstract boolean isChecked(@NotNull T entry);

  public abstract void setChecked(@NotNull T entry, boolean checked);
}
