package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.GridSelection;
import com.intellij.database.datagrid.GridSelectionImpl;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ModelIndexSet;
import com.intellij.database.datagrid.RawIndexConverter;
import com.intellij.database.datagrid.SelectionModel;
import com.intellij.database.datagrid.SelectionModelUtil;
import com.intellij.database.datagrid.SelectionModelWithViewColumns;
import com.intellij.database.datagrid.SelectionModelWithViewRows;
import com.intellij.database.datagrid.ViewIndex;
import com.intellij.database.datagrid.ViewIndexSet;
import com.intellij.database.run.ui.grid.selection.GridSelectionTracker;
import com.intellij.database.run.ui.grid.selection.GridSelectionTrackerImpl;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultListSelectionModel;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableColumnModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.IntUnaryOperator;

/** The grid's selection over the rows of a table and the columns of a column model, which may hold more columns than the table shows. */
class TableSelectionModel implements SelectionModel<GridRow, GridColumn>, SelectionModelWithViewRows, SelectionModelWithViewColumns {
  private final GridTable myTable;
  private final TableColumnModel myColumns;
  private final RawIndexConverter myConverter;
  private final DataGrid myGrid;
  private final GridSelectionTrackerImpl myTracker;

  TableSelectionModel(@NotNull GridTable table,
                      @NotNull TableColumnModel columns,
                      @NotNull RawIndexConverter converter,
                      @NotNull DataGrid grid) {
    myTable = table;
    myColumns = columns;
    myConverter = converter;
    myGrid = grid;
    myTracker = new GridSelectionTrackerImpl(myGrid, myTable);
  }

  /** Installs a selection over the rows of {@code table} and the columns of {@code columns}, which {@code converter} converts. */
  static void install(@NotNull GridTable table,
                      @NotNull TableColumnModel columns,
                      @NotNull RawIndexConverter converter,
                      @NotNull DataGrid grid) {
    TableSelectionModel tableSelectionModel = new TableSelectionModel(table, columns, converter, grid);
    table.putClientProperty(SelectionModelUtil.SELECTION_MODEL_KEY, tableSelectionModel);
  }

  @Override
  public @NotNull GridSelectionImpl store() {
    int[] rows = convert(myTable.getRowCount(), myTable::convertRowIndexToModel, myTable.getSelectedRows());
    int[] columns = convert(myColumns.getColumnCount(), this::columnIndexToModel, myColumns.getSelectedColumns());
    if (myTable.isTransposed()) {
      int[] temp = rows;
      rows = columns;
      columns = temp;
    }
    ListSelectionModel rowSelection = rowSelectionModel();
    ListSelectionModel columnSelection = columnSelectionModel();
    return new GridSelectionImpl(ModelIndexSet.forRows(myGrid, rows), ModelIndexSet.forColumns(myGrid, columns),
                                 toModelRow(rowSelection.getLeadSelectionIndex()),
                                 toModelRow(rowSelection.getAnchorSelectionIndex()),
                                 toModelColumn(columnSelection.getLeadSelectionIndex()),
                                 toModelColumn(columnSelection.getAnchorSelectionIndex()));
  }

  /** The rows of the data are laid out along the column axis in a transposed table. */
  private @NotNull ListSelectionModel rowSelectionModel() {
    return myTable.isTransposed() ? myColumns.getSelectionModel() : myTable.getSelectionModel();
  }

  /** The columns of the data are laid out along the row axis in a transposed table. */
  private @NotNull ListSelectionModel columnSelectionModel() {
    return myTable.isTransposed() ? myTable.getSelectionModel() : myColumns.getSelectionModel();
  }

  private int columnIndexToModel(int viewColumn) {
    return myColumns.getColumn(viewColumn).getModelIndex();
  }

  /** The first index of a column for {@code modelColumn}, or -1, as {@code JTable.convertColumnIndexToView} finds it. */
  private int columnIndexToView(int modelColumn) {
    if (modelColumn < 0) return modelColumn;
    for (int i = 0; i < myColumns.getColumnCount(); i++) {
      if (myColumns.getColumn(i).getModelIndex() == modelColumn) return i;
    }
    return -1;
  }

  private boolean isColumnSelected(int column) {
    return myColumns.getSelectionModel().isSelectedIndex(column);
  }

  /**
   * The data row at {@code selectionIndex} of {@link #rowSelectionModel()}, or null when the table no longer shows it.
   * A transposed table holds that index on the column axis, so it needs the column converter.
   */
  private @Nullable ModelIndex<GridRow> toModelRow(int selectionIndex) {
    int modelIndex = myTable.isTransposed() ? columnAxisToModel(selectionIndex) : rowAxisToModel(selectionIndex);
    return modelIndex < 0 ? null : ModelIndex.forRow(myGrid, modelIndex);
  }

  /** The grid column at {@code selectionIndex} of {@link #columnSelectionModel()}, or null when the table no longer shows it. */
  private @Nullable ModelIndex<GridColumn> toModelColumn(int selectionIndex) {
    int modelIndex = myTable.isTransposed() ? rowAxisToModel(selectionIndex) : columnAxisToModel(selectionIndex);
    return modelIndex < 0 ? null : ModelIndex.forColumn(myGrid, modelIndex);
  }

  /** The view index of {@code row}, or -1 when the table no longer shows it. */
  private int toViewRow(@Nullable ModelIndex<GridRow> row) {
    if (row == null) return -1;
    return myTable.isTransposed() ? columnIndexToView(row.value) : rowAxisToView(row.value);
  }

  /** The view index of {@code column}, or -1 when the table no longer shows it. */
  private int toViewColumn(@Nullable ModelIndex<GridColumn> column) {
    if (column == null) return -1;
    return myTable.isTransposed() ? rowAxisToView(column.value) : columnIndexToView(column.value);
  }

  /** The model index at {@code index} of the table's rows, or -1. */
  private int rowAxisToModel(int index) {
    return index >= 0 && index < myTable.getRowCount() ? myTable.convertRowIndexToModel(index) : -1;
  }

  /** The model index at {@code index} of the column model, or -1. */
  private int columnAxisToModel(int index) {
    return index >= 0 && index < myColumns.getColumnCount() ? columnIndexToModel(index) : -1;
  }

  /**
   * The index in the table's rows of model index {@code modelIndex}, or -1. The range check comes first because
   * {@code convertRowIndexToView} asks the sorter, which can throw.
   */
  private int rowAxisToView(int modelIndex) {
    if (modelIndex >= myTable.getModel().getRowCount()) return -1;
    int viewIndex = myTable.convertRowIndexToView(modelIndex);
    return viewIndex < myTable.getRowCount() ? viewIndex : -1;
  }

  @Override
  public @NotNull GridSelectionTracker getTracker() {
    return myTracker;
  }

  public void setColumnSelectionInterval(int idx0, int idx1) {
    setSelectionInterval(myColumns.getSelectionModel(), false, myColumns.getColumnCount() - 1, idx0, idx1);
  }

  public void addColumnSelectionInterval(int idx0, int idx1) {
    setSelectionInterval(myColumns.getSelectionModel(), true, myColumns.getColumnCount() - 1, idx0, idx1);
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
      int firstRow = myConverter.row2Model().applyAsInt(0);
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
           myColumns.getSelectionModel().isSelectionEmpty();
  }

  @Override
  public boolean isSelected(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    return isSelected(row.toView(myGrid), column.toView(myGrid));
  }

  @Override
  public boolean isSelected(@NotNull ViewIndex<GridRow> row, @NotNull ViewIndex<GridColumn> column) {
    int displayRow = myTable.isTransposed() ? column.asInteger() : row.asInteger();
    int displayColumn = myTable.isTransposed() ? row.asInteger() : column.asInteger();
    return myTable.isRowSelected(displayRow) && isColumnSelected(displayColumn);
  }

  @Override
  public boolean isSelectedColumn(@NotNull ModelIndex<GridColumn> column) {
    ViewIndex<GridColumn> colIdx = column.toView(myGrid);
    int index = colIdx.asInteger();
    return myTable.isTransposed() ? myTable.isRowSelected(index) : isColumnSelected(index);
  }

  @Override
  public boolean isSelectedRow(@NotNull ModelIndex<GridRow> row) {
    ViewIndex<GridRow> rowIdx = row.toView(myGrid);
    int index = rowIdx.asInteger();
    return myTable.isTransposed() ? isColumnSelected(index) : myTable.isRowSelected(index);
  }

  @Override
  public void setRowSelection(@NotNull ModelIndexSet<GridRow> rows, boolean selectAtLeastOneCell) {
    ViewIndexSet<GridRow> viewIndexSet = rows.toView(myGrid);
    setSelection(false, viewIndexSet.asArray());
    if (!selectAtLeastOneCell) return;
    GridSelection<GridRow, GridColumn> selection = store();
    if (selection.getSelectedColumns().size() == 0) {
      int firstColumn = myConverter.column2Model().applyAsInt(0);
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
    return myTable.isTransposed() ? myColumns.getSelectedColumnCount() : myTable.getSelectedRowCount();
  }

  @Override
  public int getSelectedColumnCount() {
    return myTable.isTransposed() ? myTable.getSelectedRowCount() : myColumns.getSelectedColumnCount();
  }

  @Override
  public @NotNull ModelIndex<GridRow> getSelectedRow() {
    int viewRowIndex = myTable.isTransposed() ? myColumns.getSelectionModel().getMinSelectionIndex() : myTable.getSelectedRow();
    return ViewIndex.forRow(myGrid, viewRowIndex >= 0 ? viewRowIndex : -1).toModel(myGrid);
  }

  @Override
  public @NotNull ModelIndexSet<GridRow> getSelectedRows() {
    return ViewIndexSet.forRows(myGrid, myTable.isTransposed() ? myColumns.getSelectedColumns() : myTable.getSelectedRows())
      .toModel(myGrid);
  }

  @Override
  public @NotNull ModelIndex<GridRow> getLeadSelectionRow() {
    int idx = myTable.isTransposed()
              ? myColumns.getSelectionModel().getLeadSelectionIndex()
              : myTable.getSelectionModel().getLeadSelectionIndex();
    return ViewIndex.forRow(myGrid, idx).toModel(myGrid);
  }

  @Override
  public @NotNull ModelIndex<GridColumn> getSelectedColumn() {
    int idx = myTable.isTransposed() ? myTable.getSelectedRow() : myColumns.getSelectionModel().getMinSelectionIndex();
    return ViewIndex.forColumn(myGrid, idx).toModel(myGrid);
  }

  @Override
  public @NotNull ModelIndex<GridColumn> getLeadSelectionColumn() {
    int idx = myTable.isTransposed()
              ? myTable.getSelectionModel().getLeadSelectionIndex()
              : myColumns.getSelectionModel().getLeadSelectionIndex();
    return ViewIndex.forColumn(myGrid, idx).toModel(myGrid);
  }

  @Override
  public @NotNull ModelIndexSet<GridColumn> getSelectedColumns() {
    return ViewIndexSet.forColumns(myGrid, myTable.isTransposed() ? myTable.getSelectedRows() : myColumns.getSelectedColumns())
      .toModel(myGrid);
  }

  @Override
  public void selectWholeRow() {
    myGrid.getAutoscrollLocker().runWithLock(() -> {
      myGrid.getHiddenColumnSelectionHolder().setWholeRowSelected(!myTable.isTransposed());
      setColumnSelectionInterval(myColumns.getColumnCount() - 1, 0);
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
      setSelection(myColumns.getSelectionModel(), false, myColumns.getColumnCount() - 1, array);
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
    // Coalesce contiguous indexes to avoid flooding Swing selection listeners on large restores.
    // Normalize and sort first: input may arrive unsorted or with duplicates (converted model/view arrays, clamping).
    int[] normalized = new int[selection.length];
    int count = 0;
    for (int index : selection) {
      if (index == -1) continue;
      normalized[count++] = index(index, maxSelectionIdx);
    }
    if (count == 0) return;
    Arrays.sort(normalized, 0, count);
    int start = normalized[0];
    int end = normalized[0];
    for (int i = 1; i < count; i++) {
      int value = normalized[i];
      if (value <= end + 1) { // contiguous or duplicate
        end = Math.max(end, value);
      }
      else {
        selectionModel.addSelectionInterval(start, end);
        start = value;
        end = value;
      }
    }
    selectionModel.addSelectionInterval(start, end);
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
    int rowCount = myTable.isTransposed() ? myColumns.getColumnCount() : myTable.getRowCount();
    int columnCount = myTable.isTransposed() ? myTable.getRowCount() : myColumns.getColumnCount();
    ModelIndexSet<GridRow> rows = ModelIndexSet.forRows(myGrid, fit(selection.getSelectedRows().asArray(), rowCount));
    ModelIndexSet<GridColumn> columns = ModelIndexSet.forColumns(myGrid, fit(selection.getSelectedColumns().asArray(), columnCount));
    return GridSelectionImpl.withSelection(selection, rows, columns);
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

    // Re-adding the rows and the columns put each lead on the last index of its interval, and anything following a lead jumps there.
    restoreLeadAndAnchor(rowSelectionModel(), toViewRow(selection.getCurrentRow()), toViewRow(selection.getRangeStartRow()));
    restoreLeadAndAnchor(columnSelectionModel(), toViewColumn(selection.getCurrentColumn()),
                         toViewColumn(selection.getRangeStartColumn()));
  }

  /** Puts the lead and the anchor back without changing the selection; a lead of -1 leaves both as they are. */
  static void restoreLeadAndAnchor(@NotNull ListSelectionModel model, int lead, int anchor) {
    if (!(model instanceof DefaultListSelectionModel selection) || lead < 0) return;
    if (anchor >= 0) selection.setAnchorSelectionIndex(anchor);
    selection.moveLeadSelectionIndex(lead); // moves the lead without changing the selection
  }
}
