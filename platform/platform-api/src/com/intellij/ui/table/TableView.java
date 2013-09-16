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
package com.intellij.ui.table;

import com.intellij.ui.TableUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.SortableColumnModel;
import com.intellij.util.ui.TableViewModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TableView<Item> extends BaseTableView implements ItemsProvider, SelectionProvider {
  public TableView() {
    this(new ListTableModel<Item>(ColumnInfo.EMPTY_ARRAY));
  }

  public TableView(final ListTableModel<Item> model) {
    super(model);
    setModelAndUpdateColumns(model);
  }

  public void setModel(final TableModel dataModel) {
    assert dataModel instanceof SortableColumnModel : "SortableColumnModel required";
    super.setModel(dataModel);
  }

  /**
   * use {@link #setModelAndUpdateColumns(com.intellij.util.ui.ListTableModel<Item>)} instead
   * @param model
   */
  @Deprecated
  public void setModel(final ListTableModel<Item> model) {
    setModelAndUpdateColumns(model);
  }
  
  public void setModelAndUpdateColumns(final ListTableModel<Item> model) {
    super.setModel(model);
    createDefaultColumnsFromModel();
    updateColumnSizes();
  }

  public ListTableModel<Item> getListTableModel() {
    return (ListTableModel<Item>)super.getModel();
  }

  public TableCellRenderer getCellRenderer(int row, int column) {
    final ColumnInfo<Item, ?> columnInfo = getListTableModel().getColumnInfos()[convertColumnIndexToModel(column)];
    final Item item = getListTableModel().getItems().get(convertRowIndexToModel(row));
    final TableCellRenderer renderer = columnInfo.getCustomizedRenderer(item, columnInfo.getRenderer(item));
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
    for (Object aSelection : selection) {
      addSelection(aSelection);
    }
  }

  public void updateColumnSizes() {
    final JTableHeader header = getTableHeader();
    final TableCellRenderer defaultRenderer = header == null? null : header.getDefaultRenderer();

    final RowSorter<? extends TableModel> sorter = getRowSorter();
    final RowSorter.SortKey sortKey = sorter == null ? null : ContainerUtil.getFirstItem(sorter.getSortKeys());
    ColumnInfo[] columns = getListTableModel().getColumnInfos();
    int[] sizeMode = new int[columns.length];
    int[] headers = new int[columns.length];
    int[] widths = new int[columns.length];
    int allColumnWidth = 0;
    int allColumnCurrent = 0;
    int varCount = 0;

    Icon sortIcon = UIManager.getIcon("Table.ascendingSortIcon");

    // calculate
    for (int i = 0; i < columns.length; i++) {
      final ColumnInfo columnInfo = columns[i];
      final TableColumn column = getColumnModel().getColumn(i);

      TableCellRenderer columnHeaderRenderer = column.getHeaderRenderer();
      if (columnHeaderRenderer == null) {
        columnHeaderRenderer = defaultRenderer;
      }
      final Component headerComponent = columnHeaderRenderer == null? null :
        columnHeaderRenderer.getTableCellRendererComponent(this, column.getHeaderValue(), false, false, 0, i);

      if (headerComponent != null) {
        headers[i] = headerComponent.getPreferredSize().width;
        // add sort icon width
        if (sorter != null && columnInfo.isSortable() && sortIcon != null &&
            (sortKey == null || sortKey.getColumn() != i)) {
          headers[i] += sortIcon.getIconWidth() + (headerComponent instanceof JLabel? ((JLabel)headerComponent).getIconTextGap() : 0);
        }
      }
      final String maxStringValue;
      final String preferredValue;
      if (columnInfo.getWidth(this) > 0) {
        sizeMode[i] = 1;
        int width = columnInfo.getWidth(this);
        widths[i] = width;
      }
      else if ((maxStringValue = columnInfo.getMaxStringValue()) != null) {
        sizeMode[i] = 2;
        widths[i] = getFontMetrics(getFont()).stringWidth(maxStringValue) + columnInfo.getAdditionalWidth();
        varCount ++;
      }
      else if ((preferredValue = columnInfo.getPreferredStringValue()) != null) {
        sizeMode[i] = 3;
        widths[i] = getFontMetrics(getFont()).stringWidth(preferredValue) + columnInfo.getAdditionalWidth();
        varCount ++;
      }
      allColumnWidth += widths[i];
      allColumnCurrent += column.getPreferredWidth();
    }
    allColumnWidth = Math.max(allColumnWidth, allColumnCurrent);

    // apply: distribute available space between resizable columns
    //        and make sure that header will fit as well
    int viewWidth = getParent() != null? getParent().getWidth() : getWidth();
    double gold = 0.5 * (3 - Math.sqrt(5));
    int addendum = varCount == 0 || viewWidth < allColumnWidth ?
                   0 : (int)((allColumnWidth < gold * viewWidth ? gold * viewWidth :
                              allColumnWidth < (1 - gold) * viewWidth ? (1 - gold) * viewWidth :
                              viewWidth) - allColumnWidth) / varCount;

    for (int i=0 ; i<columns.length; i ++) {
      TableColumn column = getColumnModel().getColumn(i);
      int width = widths[i];
      if (sizeMode[i] == 1) {
        column.setMaxWidth(width);
        column.setPreferredWidth(width);
        column.setMinWidth(width);
      }
      else if (sizeMode[i] == 2) {
        // do not shrink columns
        width = Math.max(column.getPreferredWidth(), Math.max(width + addendum, headers[i]));
        column.setPreferredWidth(width);
        column.setMaxWidth(width);
      }
      else if (sizeMode[i] == 3) {
        // do not shrink columns
        width = Math.max(column.getPreferredWidth(), Math.max(width + addendum, headers[i]));
        column.setPreferredWidth(width);
      }
    }
  }


  public Collection<Item> getSelection() {
    ArrayList<Item> result = new ArrayList<Item>();
    int[] selectedRows = getSelectedRows();
    if (selectedRows == null) return result;
    final List<Item> items = getItems();
    if (! items.isEmpty()) {
      for (int selectedRow : selectedRows) {
        final int modelIndex = convertRowIndexToModel(selectedRow);
        if (modelIndex >= 0 && modelIndex < items.size()) {
          result.add(items.get(modelIndex));
        }
      }
    }
    return result;
  }

  @Nullable
  public Item getSelectedObject() {
    final int row = getSelectedRow();
    final List<Item> list = getItems();
    return row >= 0 && row < list.size() ? list.get(convertRowIndexToModel(row)) : null;
  }

  @NotNull
  public List<Item> getSelectedObjects() {
    final int[] selectedRows = getSelectedRows();
    if (selectedRows == null || (selectedRows.length == 0)) return Collections.emptyList();
    final List<Item> items = getItems();
    final List<Item> result = new ArrayList<Item>();
    for (int selectedRow : selectedRows) {
      result.add(items.get(convertRowIndexToModel(selectedRow)));
    }
    return result;
  }

  public void addSelection(Object item) {
    List items = getItems();
    if (!items.contains(item)) return;
    int index = items.indexOf(item);
    getSelectionModel().addSelectionInterval(convertRowIndexToView(index), convertRowIndexToView(index));
    // fix cell selection case
    getColumnModel().getSelectionModel().addSelectionInterval(0, getColumnCount()-1);
  }

  public TableCellEditor getCellEditor(int row, int column) {
    final ColumnInfo<Item, ?> columnInfo = getListTableModel().getColumnInfos()[convertColumnIndexToModel(column)];
    final TableCellEditor editor = columnInfo.getEditor(getListTableModel().getItems().get(convertRowIndexToModel(row)));
    return editor == null ? super.getCellEditor(row, column) : editor;
  }

  public List<Item> getItems() {
    return getListTableModel().getItems();
  }

  public Item getRow(int row) {
    return getItems().get(convertRowIndexToModel(row));
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
