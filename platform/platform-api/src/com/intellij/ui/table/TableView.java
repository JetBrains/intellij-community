/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ui.table;

import com.intellij.ui.TableUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.SortableColumnModel;
import com.intellij.util.ui.TableViewModel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.util.*;

public class TableView<Item> extends BaseTableView implements ItemsProvider, SelectionProvider {
  public TableView() {
    this(new ListTableModel<Item>(ColumnInfo.EMPTY_ARRAY));
  }

  public TableView(final ListTableModel<Item> model) {
    super(model);
    setModel(model);
  }

  public void setModel(final ListTableModel<Item> model) {
    super.setModel(model);
    final JTableHeader header = getTableHeader();
    if (header != null) {
      header.setDefaultRenderer(new TableHeaderRenderer(model));
    }
    updateColumnSizes();
  }

  @Override
  public ListTableModel<Item> getListTableModel() {
    return (ListTableModel<Item>)super.getModel();
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

  public void setSelection(Collection<Item> selection) {
    clearSelection();
    for (Iterator iterator = selection.iterator(); iterator.hasNext();) {
      addSelection(iterator.next());
    }
  }

  public void updateColumnSizes() {
    ColumnInfo[] columns = getListTableModel().getColumnInfos();
    for (int i = 0; i < columns.length; i++) {
      ColumnInfo columnInfo = columns[i];
      TableColumn column = getColumnModel().getColumn(i);
      final String maxStringValue;
      final String preferredValue;
      if (columnInfo.getWidth(this) > 0) {
        int wight = columnInfo.getWidth(this);
        column.setMaxWidth(wight);
        column.setMinWidth(wight);
      }
      else if ((maxStringValue = columnInfo.getMaxStringValue()) != null) {
        int width = getFontMetrics(getFont()).stringWidth(maxStringValue) + columnInfo.getAdditionalWidth();
        column.setPreferredWidth(width);
        column.setMaxWidth(width);
      }
      else if ((preferredValue = columnInfo.getPreferredStringValue()) != null) {
        int width = getFontMetrics(getFont()).stringWidth(preferredValue) + columnInfo.getAdditionalWidth();
        column.setPreferredWidth(width);
      }
    }
  }


  public Collection<Item> getSelection() {
    ArrayList<Item> result = new ArrayList<Item>();
    int[] selectedRows = getSelectedRows();
    if (selectedRows == null) return result;
    final List<Item> items = getItems();
    for (int selectedRow : selectedRows) {
      if (selectedRow >= 0 && selectedRow < items.size()) {
        result.add(items.get(selectedRow));
      }
    }
    return result;
  }

  @Nullable
  public Item getSelectedObject() {
    final int row = getSelectedRow();
    final List<Item> list = getItems();
    return row >= 0 && row < list.size() ? list.get(row) : null;    
  }

  @Nullable
  public List<Item> getSelectedObjects() {
    final int[] selectedRows = getSelectedRows();
    if (selectedRows == null || (selectedRows.length == 0)) return Collections.emptyList();
    final List<Item> items = getItems();
    final List<Item> result = new ArrayList<Item>();
    for (int selectedRow : selectedRows) {
      result.add(items.get(selectedRow));
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

  public java.util.List<Item> getItems() {
    return ((ListTableModel<Item>)getModel()).getItems();
  }

  public void resortKeepSelection() {
    final int column = getSelectedColumn();
    if (column != -1) {
      SortableColumnModel model = getListTableModel();
      Collection selection = getSelection();
      model.sortByColumn(column);
      setSelection(selection);
    }
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
