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
package com.intellij.ui;

import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 * @author mike
 */
@SuppressWarnings("unchecked")
public abstract class AddEditRemovePanel<T> extends PanelWithButtons implements ComponentWithEmptyText {
  private JBTable myTable;
  private TableModel myModel;
  private List<T> myData;
  private AbstractTableModel myTableModel;
  private String myLabel;

  public AddEditRemovePanel(TableModel<T> model, List<T> data) {
    this(model, data, null);
  }

  public AddEditRemovePanel(TableModel<T> model, List<T> data, @Nullable String label) {
    myModel = model;
    myData = data;
    myLabel = label;

    initTable();
    initPanel();
  }

  @Nullable
  protected abstract T addItem();

  protected abstract boolean removeItem(T o);

  @Nullable
  protected abstract T editItem(T o);

  @Override
  protected void initPanel() {
    setLayout(new BorderLayout());

    final JPanel panel = ToolbarDecorator.createDecorator(myTable)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          doAdd();
        }
      })
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          doRemove();
        }
      })
      .setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          doEdit();
        }
      })
      .disableUpAction()
      .disableDownAction()
      .createPanel();
    add(panel, BorderLayout.CENTER);
    final String label = getLabelText();
    if (label != null) {
      UIUtil.addBorder(panel, IdeBorderFactory.createTitledBorder(label, false));
    }
  }

  protected String getLabelText(){
    return myLabel;
  }

  @Override
  public StatusText getEmptyText() {
    return myTable.getEmptyText();
  }

  protected JComponent createMainComponent(){
    initTable();

    return ScrollPaneFactory.createScrollPane(myTable);
  }

  private void initTable() {
    myTableModel = new AbstractTableModel() {
      public int getColumnCount(){
        return myModel.getColumnCount();
      }

      public int getRowCount(){
        return myData != null ? myData.size() : 0;
      }

      public Class getColumnClass(int columnIndex){
        return myModel.getColumnClass(columnIndex);
      }

      public String getColumnName(int column){
        return myModel.getColumnName(column);
      }

      public Object getValueAt(int rowIndex, int columnIndex){
        return myModel.getField(myData.get(rowIndex), columnIndex);
      }

      @Override
      public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        myModel.setValue(aValue, myData.get(rowIndex), columnIndex);
        fireTableRowsUpdated(rowIndex, rowIndex);
      }

      @Override
      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return myModel.isEditable(columnIndex);
      }
    };

    myTable = new JBTable(myTableModel);
    myTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    myTable.setStriped(true);
    myTable.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e){
        if (e.getClickCount() == 2) doEdit();
      }
    });
  }

  protected JButton[] createButtons(){
    return new JButton[0];
  }

  protected void doAdd() {
    T o = addItem();
    if (o == null) return;

    myData.add(o);
    int index = myData.size() - 1;
    myTableModel.fireTableRowsInserted(index, index);
    myTable.setRowSelectionInterval(index, index);
  }

  protected void doEdit() {
    int selected = myTable.getSelectedRow();
    if (selected >= 0) {
      T o = editItem(myData.get(selected));
      if (o != null) myData.set(selected, o);

      myTableModel.fireTableRowsUpdated(selected, selected);
    }
  }
  
  protected void doRemove() {
    final int[] selected = myTable.getSelectedRows();
    if (selected == null || selected.length == 0) return;
    for (int i = selected.length - 1; i >= 0; i--) {
      int idx = selected[i];
      if (!removeItem(myData.get(idx))) return;
      myData.remove(idx);
    }

    for (int i = selected.length - 1; i >= 0; i--) {
      int idx = selected[i];
      myTableModel.fireTableRowsDeleted(idx, idx);
    }
    int selection = selected[0];
    if (selection >= myData.size()) {
      selection = myData.size() - 1;
    }
    if (selection >= 0) {
      myTable.setRowSelectionInterval(selection, selection);
    }   
  }

  public void setData(List<T> data) {
    myData = data;
    myTableModel.fireTableDataChanged();
  }

  public List<T> getData() {
    return myData;
  }

  public void setRenderer(int index, TableCellRenderer renderer) {
      myTable.getColumn(myModel.getColumnName(index)).setCellRenderer(renderer);
  }

  public void setSelected(Object o) {
    for(int i = 0; i < myTableModel.getRowCount(); ++i) {
      if (myData.get(i).equals(o)) {
        myTable.getSelectionModel().setSelectionInterval(i,i);
        break;
      }
    }
  }

  public JBTable getTable() {
    return myTable;
  }

  public abstract static class TableModel<T> {

    public abstract int getColumnCount();
    public abstract String getColumnName(int columnIndex);
    public abstract Object getField(T o, int columnIndex);

    public Class getColumnClass(int columnIndex) { return String.class; }
    public boolean isEditable(int column) {return false; }
    public void setValue(Object aValue, T data, int columnIndex) {}
  }
}
