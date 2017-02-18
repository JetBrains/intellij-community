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

import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.TableUtil;
import com.intellij.util.SmartList;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TableView<Item> extends BaseTableView implements ItemsProvider, SelectionProvider {

  private boolean myInStopEditing = false;

  public TableView() {
    this(new ListTableModel<>(ColumnInfo.EMPTY_ARRAY));
  }

  public TableView(final ListTableModel<Item> model) {
    super(model);
    setModelAndUpdateColumns(model);
  }

  @Override
  public void setModel(@NotNull final TableModel dataModel) {
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

  @Override
  public TableCellRenderer getCellRenderer(int row, int column) {
    final ColumnInfo<Item, ?> columnInfo = getListTableModel().getColumnInfos()[convertColumnIndexToModel(column)];
    final Item item = getRow(row);
    final TableCellRenderer renderer = columnInfo.getCustomizedRenderer(item, columnInfo.getRenderer(item));
    if (renderer == null) {
      return super.getCellRenderer(row, column);
    }
    else {
      return renderer;
    }
  }

  @Override
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
    ColumnInfo[] columnInfos = getListTableModel().getColumnInfos();
    TableColumnModel columnModel = getColumnModel();
    int visibleColumnCount = columnModel.getColumnCount();
    int[] sizeMode = new int[visibleColumnCount];
    int[] headers = new int[visibleColumnCount];
    int[] widths = new int[visibleColumnCount];
    int allColumnWidth = 0;
    int allColumnCurrent = 0;
    int varCount = 0;

    Icon sortIcon = UIManager.getIcon("Table.ascendingSortIcon");

    // calculate
    for (int i = 0; i < visibleColumnCount; i++) {
      final TableColumn column = columnModel.getColumn(i);
      final ColumnInfo columnInfo = columnInfos[column.getModelIndex()];

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

    for (int i = 0 ; i < visibleColumnCount; i++) {
      TableColumn column = columnModel.getColumn(i);
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


  @Override
  public Collection<Item> getSelection() {
    return getSelectedObjects();
  }

  @Nullable
  public Item getSelectedObject() {
    final int row = getSelectedRow();
    ListTableModel<Item> model = getListTableModel();
    return row >= 0 && row < model.getRowCount() ? model.getRowValue(convertRowIndexToModel(row)) : null;
  }

  @NotNull
  public List<Item> getSelectedObjects() {
    ListSelectionModel selectionModel = getSelectionModel();
    int minSelectionIndex = selectionModel.getMinSelectionIndex();
    int maxSelectionIndex = selectionModel.getMaxSelectionIndex();
    if (minSelectionIndex == -1 || maxSelectionIndex == -1) {
      return Collections.emptyList();
    }

    List<Item> result = new SmartList<>();
    ListTableModel<Item> model = getListTableModel();
    for (int i = minSelectionIndex; i <= maxSelectionIndex; i++) {
      if (selectionModel.isSelectedIndex(i)) {
        int modelIndex = convertRowIndexToModel(i);
        if (modelIndex >= 0 && modelIndex < model.getRowCount()) {
          result.add(model.getRowValue(modelIndex));
        }
      }
    }
    return result;
  }

  @Override
  public void addSelection(Object item) {
    @SuppressWarnings("unchecked")
    int index = getListTableModel().indexOf((Item)item);
    if (index < 0) {
      return;
    }

    getSelectionModel().addSelectionInterval(convertRowIndexToView(index), convertRowIndexToView(index));
    // fix cell selection case
    getColumnModel().getSelectionModel().addSelectionInterval(0, getColumnCount()-1);
  }

  @Override
  public TableCellEditor getCellEditor(int row, int column) {
    @SuppressWarnings("unchecked")
    TableCellEditor editor = getListTableModel().getColumnInfos()[convertColumnIndexToModel(column)].getEditor(getRow(row));
    return editor == null ? super.getCellEditor(row, column) : editor;
  }

  @Override
  public List<Item> getItems() {
    return getListTableModel().getItems();
  }

  public Item getRow(int row) {
    return getListTableModel().getRowValue(convertRowIndexToModel(row));
  }

  public void setMinRowHeight(int i) {
    setRowHeight(Math.max(i, getRowHeight()));
  }

  public JTable getComponent() {
    return this;
  }

  public TableViewModel<Item> getTableViewModel() {
    return getListTableModel();
  }

  public void stopEditing() {
    if (!myInStopEditing) {
      try {
        myInStopEditing = true;
        TableUtil.stopEditing(this);
      }
      finally {
        myInStopEditing = false;
      }
    }
  }

  @Override
  protected void createDefaultRenderers() {
    super.createDefaultRenderers();

    UIDefaults.LazyValue booleanRenderer = new UIDefaults.LazyValue() {
      @Override
      public Object createValue(@NotNull UIDefaults table) {
        DefaultCellEditor editor = new DefaultCellEditor(GuiUtils.createUndoableTextField());
        editor.setClickCountToStart(1);
        return new BooleanTableCellRenderer();
      }
    };
    //noinspection unchecked
    defaultRenderersByColumnClass.put(boolean.class, booleanRenderer);
    //noinspection unchecked
    defaultRenderersByColumnClass.put(Boolean.class, booleanRenderer);
  }

  @Override
  protected void createDefaultEditors() {
    super.createDefaultEditors();

    //noinspection unchecked
    defaultEditorsByColumnClass.put(String.class, new UIDefaults.LazyValue() {
      @Override
      public Object createValue(@NotNull UIDefaults table) {
        DefaultCellEditor editor = new DefaultCellEditor(GuiUtils.createUndoableTextField());
        editor.setClickCountToStart(1);
        return editor;
      }
    });

    //noinspection unchecked
    defaultEditorsByColumnClass.put(boolean.class, defaultEditorsByColumnClass.get(Boolean.class));
  }
}
