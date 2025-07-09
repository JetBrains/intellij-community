package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.grid.selection.GridSelectionTracker;
import com.intellij.database.run.ui.grid.selection.GridSelectionTrackerImpl;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.IntUnaryOperator;

class TableSelectionModel implements SelectionModel<GridRow, GridColumn>, SelectionModelWithViewRows, SelectionModelWithViewColumns {
  private final TableResultView myTable;
  private final DataGrid myGrid;
  private final GridSelectionTrackerImpl myTracker;

  TableSelectionModel(@NotNull TableResultView table, @NotNull DataGrid grid) {
    myTable = table;
    myGrid = grid;
    myTracker = new GridSelectionTrackerImpl(myGrid, myTable);
    myTable.putClientProperty(SelectionModelUtil.SELECTION_MODEL_KEY, this);
  }

  @Override
  public @NotNull GridSelectionImpl store() {
    int[] rows = convert(myTable.getRowCount(), myTable::convertRowIndexToModel, myTable.getSelectedRows());
    int[] columns = convert(myTable.getColumnCount(), myTable::convertColumnIndexToModel, myTable.getSelectedColumns());
    if (myTable.isTransposed()) {
      int[] temp = rows;
      rows = columns;
      columns = temp;
    }
    return new GridSelectionImpl(ModelIndexSet.forRows(myGrid, rows), ModelIndexSet.forColumns(myGrid, columns));
  }

  @Override
  public @NotNull GridSelectionTracker getTracker() {
    return myTracker;
  }

  public void setColumnSelectionInterval(int idx0, int idx1) {
    setSelectionInterval(myTable.getColumnModel().getSelectionModel(), false, myTable.getColumnCount() - 1, idx0, idx1);
  }

  public void addColumnSelectionInterval(int idx0, int idx1) {
    setSelectionInterval(myTable.getColumnModel().getSelectionModel(), true, myTable.getColumnCount() - 1, idx0, idx1);
  }

  public void setRowSelectionInterval(int idx0, int idx1) {
    setSelectionInterval(myTable.getSelectionModel(), false, myTable.getRowCount() - 1, idx0, idx1);
  }

  public void addRowSelectionInterval(int idx0, int idx1) {
    setSelectionInterval(myTable.getSelectionModel(), true, myTable.getRowCount() - 1, idx0, idx1);
  }

  @Override
  public void setColumnSelection(@NotNull ModelIndexSet<GridColumn> columns, boolean selectAtLeastOneCell) {
    ViewIndexSet<GridColumn> viewIndexSet = columns.toView(myGrid);
    setSelection(true, viewIndexSet.asArray());
    if (!selectAtLeastOneCell) return;
    GridSelectionImpl selection = store();
    if (selection.getSelectedRows().size() == 0) {
      int firstRow = myTable.getRawIndexConverter().row2Model().applyAsInt(0);
      setRowSelection(ModelIndexSet.forRows(myGrid, firstRow), false);
    }
  }

  @Override
  public void setColumnSelection(@NotNull ModelIndex<GridColumn> column, boolean selectAtLeastOneCell) {
    setColumnSelection(ModelIndexSet.forColumns(myGrid, column.value), selectAtLeastOneCell);
  }

  @Override
  public boolean isSelectionEmpty() {
    return myTable.getSelectionModel().isSelectionEmpty() ||
           myTable.getColumnModel().getSelectionModel().isSelectionEmpty();
  }

  @Override
  public boolean isSelected(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    return isSelected(row.toView(myGrid), column.toView(myGrid));
  }

  @Override
  public boolean isSelected(@NotNull ViewIndex<GridRow> row, @NotNull ViewIndex<GridColumn> column) {
    int displayRow = myTable.isTransposed() ? column.asInteger() : row.asInteger();
    int displayColumn = myTable.isTransposed() ? row.asInteger() : column.asInteger();
    return myTable.isRowSelected(displayRow) && myTable.isColumnSelected(displayColumn);
  }

  @Override
  public boolean isSelectedColumn(@NotNull ModelIndex<GridColumn> column) {
    ViewIndex<GridColumn> colIdx = column.toView(myGrid);
    int index = colIdx.asInteger();
    return myTable.isTransposed() ? myTable.isRowSelected(index) : myTable.isColumnSelected(index);
  }

  @Override
  public boolean isSelectedRow(@NotNull ModelIndex<GridRow> row) {
    ViewIndex<GridRow> rowIdx = row.toView(myGrid);
    int index = rowIdx.asInteger();
    return myTable.isTransposed() ? myTable.isColumnSelected(index) : myTable.isRowSelected(index);
  }

  @Override
  public void setRowSelection(@NotNull ModelIndexSet<GridRow> rows, boolean selectAtLeastOneCell) {
    ViewIndexSet<GridRow> viewIndexSet = rows.toView(myGrid);
    setSelection(false, viewIndexSet.asArray());
    if (!selectAtLeastOneCell) return;
    GridSelection<GridRow, GridColumn> selection = store();
    if (selection.getSelectedColumns().size() == 0) {
      int firstColumn = myTable.getRawIndexConverter().column2Model().applyAsInt(0);
      setColumnSelection(ModelIndexSet.forColumns(myGrid, firstColumn), false);
    }
  }

  @Override
  public void setSelection(@NotNull ModelIndexSet<GridRow> rows, @NotNull ModelIndexSet<GridColumn> columns) {
    myTable.disableSelectionListeners(() -> {
      setRowSelection(rows, false);
      setColumnSelection(columns, false);
    });
    myTable.fireSelectionChanged();
  }

  @Override
  public void setSelection(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    myTable.disableSelectionListeners(() -> {
      setRowSelection(row, false);
      setColumnSelection(column, false);
    });
    myTable.fireSelectionChanged();
  }

  @Override
  public void setRowSelection(@NotNull ModelIndex<GridRow> row, boolean selectAtLeastOneCell) {
    setRowSelection(ModelIndexSet.forRows(myGrid, row.value), selectAtLeastOneCell);
  }

  @Override
  public int getSelectedRowCount() {
    return myTable.isTransposed() ? myTable.getSelectedColumnCount() : myTable.getSelectedRowCount();
  }

  @Override
  public int getSelectedColumnCount() {
    return myTable.isTransposed() ? myTable.getSelectedRowCount() : myTable.getSelectedColumnCount();
  }

  @Override
  public @NotNull ModelIndex<GridRow> getSelectedRow() {
    int viewRowIndex = myTable.isTransposed() ? myTable.getSelectedColumn() : myTable.getSelectedRow();
    return ViewIndex.forRow(myGrid, viewRowIndex >= 0 ? viewRowIndex : -1).toModel(myGrid);
  }

  @Override
  public @NotNull ModelIndexSet<GridRow> getSelectedRows() {
    return ViewIndexSet.forRows(myGrid, myTable.isTransposed() ? myTable.getSelectedColumns() : myTable.getSelectedRows()).toModel(myGrid);
  }

  @Override
  public @NotNull ModelIndex<GridRow> getLeadSelectionRow() {
    int idx = myTable.isTransposed()
              ? myTable.getColumnModel().getSelectionModel().getLeadSelectionIndex()
              : myTable.getSelectionModel().getLeadSelectionIndex();
    return ViewIndex.forRow(myGrid, idx).toModel(myGrid);
  }

  @Override
  public @NotNull ModelIndex<GridColumn> getSelectedColumn() {
    return ViewIndex.forColumn(myGrid, myTable.isTransposed() ? myTable.getSelectedRow() : myTable.getSelectedColumn()).toModel(myGrid);
  }

  @Override
  public @NotNull ModelIndex<GridColumn> getLeadSelectionColumn() {
    int idx = myTable.isTransposed()
              ? myTable.getSelectionModel().getLeadSelectionIndex()
              : myTable.getColumnModel().getSelectionModel().getLeadSelectionIndex();
    return ViewIndex.forColumn(myGrid, idx).toModel(myGrid);
  }

  @Override
  public @NotNull ModelIndexSet<GridColumn> getSelectedColumns() {
    return ViewIndexSet.forColumns(myGrid, myTable.isTransposed() ? myTable.getSelectedRows() : myTable.getSelectedColumns())
      .toModel(myGrid);
  }

  @Override
  public void selectWholeRow() {
    myGrid.getAutoscrollLocker().runWithLock(() -> {
      myGrid.getHiddenColumnSelectionHolder().setWholeRowSelected(!myTable.isTransposed());
      setColumnSelectionInterval(myTable.getColumnCount() - 1, 0);
    });
  }

  @Override
  public void selectWholeColumn() {
    myGrid.getAutoscrollLocker().runWithLock(() -> setRowSelectionInterval(myTable.getRowCount() - 1, 0));
  }

  @Override
  public void clearSelection() {
    myTable.clearSelection();
  }

  private void setSelection(boolean columns, int[] array) {
    if (myTable.isTransposed() && columns || !myTable.isTransposed() && !columns) {
      setSelection(myTable.getSelectionModel(), false, myTable.getRowCount() - 1, array);
    }
    else {
      setSelection(myTable.getColumnModel().getSelectionModel(), false, myTable.getColumnCount() - 1, array);
    }
  }

  @Override
  public void addRowSelection(@NotNull ModelIndexSet<GridRow> selection) {
    setSelection(myTable.getSelectionModel(), true, myTable.getRowCount() - 1, selection.asArray());
  }

  @Override
  public int selectedViewRowsCount() {
    return myTable.isTransposed() ? getSelectedRows().size() : getSelectedColumns().size();
  }

  @Override
  public int selectedViewColumnsCount() {
    return myTable.isTransposed() ? getSelectedColumns().size() : getSelectedRows().size();
  }

  private static void setSelection(ListSelectionModel selectionModel, boolean add, int maxSelectionIdx, int... selection) {
    if (maxSelectionIdx < 0) return;
    if (!add) selectionModel.clearSelection();
    for (int index : selection) {
      if (index == -1) continue;
      setSelectionInterval(selectionModel, true, maxSelectionIdx, index, index);
    }
  }

  private static void setSelectionInterval(ListSelectionModel selectionModel, boolean add, int maxSelectionIdx, int idx0, int idx1) {
    if (maxSelectionIdx < 0) return;
    idx0 = index(idx0, maxSelectionIdx);
    idx1 = index(idx1, maxSelectionIdx);
    if (add) {
      selectionModel.addSelectionInterval(idx0, idx1);
    }
    else {
      selectionModel.setSelectionInterval(idx0, idx1);
    }
  }

  private static int index(int idx, int maxIdx) {
    return Math.max(0, Math.min(idx, maxIdx));
  }

  private static int[] convert(int maxIndex, IntUnaryOperator function, int[] indices) {
    return Arrays.stream(indices)
      .filter(index -> index < maxIndex && index >= 0)
      .map(function)
      .filter(index -> index != -1)
      .toArray();
  }

  @Override
  public @NotNull GridSelection<GridRow, GridColumn> fit(@NotNull GridSelection<GridRow, GridColumn> selection) {
    int rowCount = myTable.isTransposed() ? myTable.getColumnCount() : myTable.getRowCount();
    int columnCount = myTable.isTransposed() ? myTable.getRowCount() : myTable.getColumnCount();
    ModelIndexSet<GridRow> rows = ModelIndexSet.forRows(myGrid, fit(selection.getSelectedRows().asArray(), rowCount));
    ModelIndexSet<GridColumn> columns = ModelIndexSet.forColumns(myGrid, fit(selection.getSelectedColumns().asArray(), columnCount));
    return new GridSelectionImpl(rows, columns);
  }

  private static int[] fit(int @NotNull [] indices, int max) {
    ArrayList<Integer> newIndices = new ArrayList<>();
    for (int index : indices) {
      if (index < max) newIndices.add(index);
    }
    return ArrayUtil.toIntArray(newIndices);
  }

  @Override
  public void restore(@NotNull GridSelection<GridRow, GridColumn> selection) {
    myTable.clearSelection();
    SelectionModel<GridRow, GridColumn> selectionModel = myGrid.getSelectionModel();
    selectionModel.setSelection(selection.getSelectedRows(), selection.getSelectedColumns());
  }
}

