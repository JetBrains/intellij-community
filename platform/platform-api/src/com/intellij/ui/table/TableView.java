// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import javax.swing.border.Border;
import javax.swing.event.TableModelEvent;
import javax.swing.table.*;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TableView<Item> extends BaseTableView implements SelectionProvider {

  private boolean myInStopEditing = false;

  public TableView() {
    this(new ListTableModel<>(ColumnInfo.EMPTY_ARRAY));
  }

  public TableView(final ListTableModel<Item> model) {
    super(model);
    setModelAndUpdateColumns(model);
  }

  @Override
  public void setModel(final @NotNull TableModel dataModel) {
    assert dataModel instanceof SortableColumnModel : "SortableColumnModel required";
    super.setModel(dataModel);
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
    // Swing GUI designer sets default model (assert in setModel() not worked)
    if (!(getModel() instanceof ListTableModel)) {
      return super.getCellRenderer(row, column);
    }
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

  public void setSelection(Collection<? extends Item> selection) {
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
    Border border = UIManager.getBorder("Table.cellNoFocusBorder");
    Insets borderInsets = border == null ? null : border.getBorderInsets(this);
    int borderWidth = borderInsets == null ? 0 : borderInsets.left + borderInsets.right;
    if (getShowVerticalLines()) borderWidth += 2;

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
      int columnWidth = columnInfo.getWidth(this);
      if (columnWidth > 0) {
        sizeMode[i] = 1;
        widths[i] = columnWidth;
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
      widths[i] += borderWidth;
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

  public @Nullable Item getSelectedObject() {
    final int row = getSelectedRow();
    ListTableModel<Item> model = getListTableModel();
    return row >= 0 && row < model.getRowCount() ? model.getRowValue(convertRowIndexToModel(row)) : null;
  }

  public @NotNull List<Item> getSelectedObjects() {
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
