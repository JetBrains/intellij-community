package com.intellij.ui.table;

import com.intellij.ui.TableUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.SortableColumnModel;
import com.intellij.util.ui.TableViewModel;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class TableView extends BaseTableView implements ItemsProvider, SelectionProvider{
  public TableView(final ListTableModel model) {
    super(model);
    setModel(model);
    setSizes();
  }

  public void setModel(final ListTableModel model) {
    super.setModel(model);
    getTableHeader().setDefaultRenderer(new TableHeaderRenderer(model));

  }

  public TableCellRenderer getCellRenderer(int row, int column) {
    ColumnInfo columnInfo = getListTableModel().getColumnInfos()[convertColumnIndexToModel(column)];
    TableCellRenderer renderer = columnInfo.getRenderer(getListTableModel().getItems().get(row));
    if (renderer == null) {
      return super.getCellRenderer(row, column);
    }
    else {
      return renderer;
    }
  }

  public void tableChanged(TableModelEvent e) {
    if (isEditing()) getCellEditor().cancelCellEditing();
    super.tableChanged(e);
  }

  private void setSelection(Collection selection) {
    clearSelection();
    for (Iterator iterator = selection.iterator(); iterator.hasNext();) {
      addSelection(iterator.next());
    }
  }

  private void setSizes() {
    ColumnInfo[] columns = getListTableModel().getColumnInfos();
    for (int i = 0; i < columns.length; i++) {
      ColumnInfo columnInfo = columns[i];
      TableColumn column = getColumnModel().getColumn(i);
      if (columnInfo.getWidth(this) > 0) {
        int wight = columnInfo.getWidth(this);
        column.setMaxWidth(wight);
        column.setMinWidth(wight);
      }
    }
  }


  public Collection getSelection() {
    ArrayList result = new ArrayList();
    int[] selectedRows = getSelectedRows();
    if (selectedRows == null) return result;
    for (int i = 0; i < selectedRows.length; i++) {
      int selectedRow = selectedRows[i];
      result.add(getItems().get(selectedRow));
    }
    return result;
  }

  public void addSelection(Object item) {
    List items = getItems();
    if (!items.contains(item)) return;
    int index = items.indexOf(item);
    getSelectionModel().addSelectionInterval(index, index);
  }

  public TableCellEditor getCellEditor(int row, int column) {
    ColumnInfo columnInfo = getListTableModel().getColumnInfos()[convertColumnIndexToModel(column)];
    TableCellEditor editor = columnInfo.getEditor(getListTableModel().getItems().get(row));
    if (editor == null) {
      return super.getCellEditor(row, column);
    }
    else {
      return editor;
    }
  }

  public java.util.List getItems() {
    return ((ListTableModel)getModel()).getItems();
  }

  protected void onHeaderClicked(int column) {
    SortableColumnModel model = getListTableModel();
    Collection selection = getSelection();
    model.sortByColumn(column);
    setSelection(selection);
  }

  public void setMinRowHeight(int i) {
    setRowHeight(Math.max(i, getRowHeight()));
  }

  public JTable getComponent() {
    return this;
  }

  public TableViewModel getTableViewModel() {
    return getListTableModel();
  }

  public void stopEditing() {
    TableUtil.stopEditing(this);
  }
}
