package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.GridSelection;
import com.intellij.database.datagrid.GridSelectionImpl;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ModelIndexSet;
import com.intellij.database.datagrid.SelectionModel;
import com.intellij.database.datagrid.SelectionModelWithViewColumns;
import com.intellij.database.datagrid.SelectionModelWithViewRows;
import com.intellij.database.datagrid.ViewIndex;
import com.intellij.database.datagrid.ViewIndexSet;
import com.intellij.database.run.ui.grid.selection.GridSelectionTracker;
import com.intellij.database.run.ui.grid.selection.GridSelectionTrackerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultListSelectionModel;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.IntUnaryOperator;

/**
 * The grid's selection, kept in the view's row selection and column model and converted through its row sorter and data model. On each
 * transpose, {@link TableRowOrder} replaces the sorter and the view replaces the data model.
 */
class TableSelectionModel implements SelectionModel<GridRow, GridColumn>, SelectionModelWithViewRows, SelectionModelWithViewColumns {
  private final DataGrid myGrid;
  private final ListSelectionModel myRowSelection;
  /** The view's shared row sorter, which {@link TableRowOrder} sets before the view's constructor returns. */
  @SuppressWarnings("NotNullFieldNotInitialized")
  private @NotNull RowSorter<? extends TableModel> myRowSorter;
  private final TableColumnModel myColumnModel;
  private final GridSelectionEvents myEvents;
  private final GridSelectionTrackerImpl myTracker;
  /** The data model the tables show, which the view gives again on each transpose. */
  private GridTableModel myDataModel;

  TableSelectionModel(@NotNull DataGrid grid,
                      @NotNull ListSelectionModel rowSelection,
                      @NotNull TableColumnModel columnModel,
                      @NotNull GridSelectionEvents events,
                      @NotNull GridTableModel dataModel,
                      @NotNull Disposable parent) {
    myGrid = grid;
    myRowSelection = rowSelection;
    myColumnModel = columnModel;
    myEvents = events;
    myDataModel = dataModel;
    myTracker = new GridSelectionTrackerImpl(myGrid, parent);
  }

  void setDataModel(@NotNull GridTableModel model) {
    myDataModel = model;
  }

  void setRowSorter(@NotNull RowSorter<? extends TableModel> sorter) {
    myRowSorter = sorter;
  }

  private boolean isTransposed() {
    return myDataModel instanceof TransposedGridTableModel;
  }

  /** The number of rows the tables show, as {@code JTable.getRowCount} counts them with a sorter. */
  private int rowCount() {
    return myRowSorter.getViewRowCount();
  }

  private int convertRowIndexToModel(int viewRow) {
    return myRowSorter.convertRowIndexToModel(viewRow);
  }

  private int convertRowIndexToView(int modelRow) {
    return myRowSorter.convertRowIndexToView(modelRow);
  }

  /*
   * The row selection reads below copy JTable's, which read the row selection as it is: JTable clamps nothing to its row count.
   */

  private int[] selectedRows() {
    return myRowSelection.getSelectedIndices();
  }

  private int selectedRowCount() {
    return myRowSelection.getSelectedItemsCount();
  }

  private int selectedRow() {
    return myRowSelection.getMinSelectionIndex();
  }

  private boolean isRowSelected(int row) {
    return myRowSelection.isSelectedIndex(row);
  }

  @Override
  public @NotNull GridSelectionImpl store() {
    int[] rows = convert(rowCount(), this::convertRowIndexToModel, selectedRows());
    int[] columns = convert(myColumnModel.getColumnCount(), this::columnIndexToModel, myColumnModel.getSelectedColumns());
    if (isTransposed()) {
      int[] temp = rows;
      rows = columns;
      columns = temp;
    }
    ListSelectionModel rowSelection = dataRowSelection();
    ListSelectionModel columnSelection = dataColumnSelection();
    return new GridSelectionImpl(ModelIndexSet.forRows(myGrid, rows), ModelIndexSet.forColumns(myGrid, columns),
                                 toModelRow(rowSelection.getLeadSelectionIndex()),
                                 toModelRow(rowSelection.getAnchorSelectionIndex()),
                                 toModelColumn(columnSelection.getLeadSelectionIndex()),
                                 toModelColumn(columnSelection.getAnchorSelectionIndex()));
  }

  /** The selection of the data's rows: the view's row selection, or its column selection while transposed. */
  private @NotNull ListSelectionModel dataRowSelection() {
    return isTransposed() ? myColumnModel.getSelectionModel() : myRowSelection;
  }

  /** The selection of the data's columns: the view's column selection, or its row selection while transposed. */
  private @NotNull ListSelectionModel dataColumnSelection() {
    return isTransposed() ? myRowSelection : myColumnModel.getSelectionModel();
  }

  private int columnIndexToModel(int viewColumn) {
    return myColumnModel.getColumn(viewColumn).getModelIndex();
  }

  /** The first index of a column for {@code modelColumn}, or -1, as {@code JTable.convertColumnIndexToView} finds it. */
  private int columnAxisToView(int modelColumn) {
    if (modelColumn < 0) return modelColumn;
    for (int i = 0; i < myColumnModel.getColumnCount(); i++) {
      if (myColumnModel.getColumn(i).getModelIndex() == modelColumn) return i;
    }
    return -1;
  }

  private boolean isColumnSelected(int column) {
    return myColumnModel.getSelectionModel().isSelectedIndex(column);
  }

  /**
   * The data row at {@code selectionIndex} of {@link #dataRowSelection()}, or null when the view's models no longer hold it.
   * Transposed, that index is on the column axis.
   */
  private @Nullable ModelIndex<GridRow> toModelRow(int selectionIndex) {
    int modelIndex = isTransposed() ? columnAxisToModel(selectionIndex) : rowAxisToModel(selectionIndex);
    return modelIndex < 0 ? null : ModelIndex.forRow(myGrid, modelIndex);
  }

  /** The grid column at {@code selectionIndex} of {@link #dataColumnSelection()}, or null when the view's models no longer hold it. */
  private @Nullable ModelIndex<GridColumn> toModelColumn(int selectionIndex) {
    int modelIndex = isTransposed() ? rowAxisToModel(selectionIndex) : columnAxisToModel(selectionIndex);
    return modelIndex < 0 ? null : ModelIndex.forColumn(myGrid, modelIndex);
  }

  /** The index of {@code row} in {@link #dataRowSelection()}, or -1 when the view's models no longer hold it. */
  private int toViewRow(@Nullable ModelIndex<GridRow> row) {
    if (row == null) return -1;
    return isTransposed() ? columnAxisToView(row.value) : rowAxisToView(row.value);
  }

  /** The index of {@code column} in {@link #dataColumnSelection()}, or -1 when the view's models no longer hold it. */
  private int toViewColumn(@Nullable ModelIndex<GridColumn> column) {
    if (column == null) return -1;
    return isTransposed() ? rowAxisToView(column.value) : columnAxisToView(column.value);
  }

  /** The model index at {@code index} of the sorter's rows, or -1. */
  private int rowAxisToModel(int index) {
    return index >= 0 && index < rowCount() ? convertRowIndexToModel(index) : -1;
  }

  /** The model index at {@code index} of the column model, or -1. */
  private int columnAxisToModel(int index) {
    return index >= 0 && index < myColumnModel.getColumnCount() ? columnIndexToModel(index) : -1;
  }

  /**
   * The index in the sorter's rows of model index {@code modelIndex}, or -1. The range check comes first because
   * {@code convertRowIndexToView} asks the sorter, which can throw.
   */
  private int rowAxisToView(int modelIndex) {
    if (modelIndex >= myDataModel.getRowCount()) return -1;
    int viewIndex = convertRowIndexToView(modelIndex);
    return viewIndex < rowCount() ? viewIndex : -1;
  }

  @Override
  public @NotNull GridSelectionTracker getTracker() {
    return myTracker;
  }

  /*
   * The interval setters below take the tables' axes, not the data's: view rows, and positions in the view's column model. Transposed, a
   * table row is a data column. Both ends are clamped to the axis.
   */

  public void setColumnSelectionInterval(int idx0, int idx1) {
    setSelectionInterval(myColumnModel.getSelectionModel(), false, myColumnModel.getColumnCount() - 1, idx0, idx1);
  }

  public void addColumnSelectionInterval(int idx0, int idx1) {
    setSelectionInterval(myColumnModel.getSelectionModel(), true, myColumnModel.getColumnCount() - 1, idx0, idx1);
  }

  public void setRowSelectionInterval(int idx0, int idx1) {
    setSelectionInterval(myRowSelection, false, rowCount() - 1, idx0, idx1);
  }

  public void addRowSelectionInterval(int idx0, int idx1) {
    setSelectionInterval(myRowSelection, true, rowCount() - 1, idx0, idx1);
  }

  @Override
  public void setColumnSelection(@NotNull ModelIndexSet<GridColumn> columns, boolean selectAtLeastOneCell) {
    ViewIndexSet<GridColumn> viewIndexSet = columns.toView(myGrid);
    setSelection(true, viewIndexSet.asArray());
    if (!selectAtLeastOneCell) return;
    GridSelectionImpl selection = store();
    if (selection.getSelectedRows().size() == 0) {
      ModelIndex<GridRow> firstRow = toModelRow(0);
      if (firstRow != null) setRowSelection(firstRow, false);
    }
  }

  @Override
  public void setColumnSelection(@NotNull ModelIndex<GridColumn> column, boolean selectAtLeastOneCell) {
    setColumnSelection(ModelIndexSet.forColumns(myGrid, column.value), selectAtLeastOneCell);
  }

  @Override
  public boolean isSelectionEmpty() {
    return myRowSelection.isSelectionEmpty() ||
           myColumnModel.getSelectionModel().isSelectionEmpty();
  }

  @Override
  public boolean isSelected(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    return isSelected(row.toView(myGrid), column.toView(myGrid));
  }

  @Override
  public boolean isSelected(@NotNull ViewIndex<GridRow> row, @NotNull ViewIndex<GridColumn> column) {
    int displayRow = isTransposed() ? column.asInteger() : row.asInteger();
    int displayColumn = isTransposed() ? row.asInteger() : column.asInteger();
    return isRowSelected(displayRow) && isColumnSelected(displayColumn);
  }

  @Override
  public boolean isSelectedColumn(@NotNull ModelIndex<GridColumn> column) {
    ViewIndex<GridColumn> colIdx = column.toView(myGrid);
    int index = colIdx.asInteger();
    return isTransposed() ? isRowSelected(index) : isColumnSelected(index);
  }

  @Override
  public boolean isSelectedRow(@NotNull ModelIndex<GridRow> row) {
    ViewIndex<GridRow> rowIdx = row.toView(myGrid);
    int index = rowIdx.asInteger();
    return isTransposed() ? isColumnSelected(index) : isRowSelected(index);
  }

  @Override
  public void setRowSelection(@NotNull ModelIndexSet<GridRow> rows, boolean selectAtLeastOneCell) {
    ViewIndexSet<GridRow> viewIndexSet = rows.toView(myGrid);
    setSelection(false, viewIndexSet.asArray());
    if (!selectAtLeastOneCell) return;
    GridSelection<GridRow, GridColumn> selection = store();
    if (selection.getSelectedColumns().size() == 0) {
      ModelIndex<GridColumn> firstColumn = toModelColumn(0);
      if (firstColumn != null) setColumnSelection(firstColumn, false);
    }
  }

  @Override
  public void setSelection(@NotNull ModelIndexSet<GridRow> rows, @NotNull ModelIndexSet<GridColumn> columns) {
    myEvents.runAsOneChange(() -> {
      setRowSelection(rows, false);
      setColumnSelection(columns, false);
      myEvents.requestNotification();
    });
  }

  @Override
  public void setSelection(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    myEvents.runAsOneChange(() -> {
      setRowSelection(row, false);
      setColumnSelection(column, false);
      myEvents.requestNotification();
    });
  }

  @Override
  public void setRowSelection(@NotNull ModelIndex<GridRow> row, boolean selectAtLeastOneCell) {
    setRowSelection(ModelIndexSet.forRows(myGrid, row.value), selectAtLeastOneCell);
  }

  @Override
  public int getSelectedRowCount() {
    return isTransposed() ? myColumnModel.getSelectedColumnCount() : selectedRowCount();
  }

  @Override
  public int getSelectedColumnCount() {
    return isTransposed() ? selectedRowCount() : myColumnModel.getSelectedColumnCount();
  }

  @Override
  public @NotNull ModelIndex<GridRow> getSelectedRow() {
    int viewRowIndex = isTransposed() ? myColumnModel.getSelectionModel().getMinSelectionIndex() : selectedRow();
    return ViewIndex.forRow(myGrid, viewRowIndex >= 0 ? viewRowIndex : -1).toModel(myGrid);
  }

  @Override
  public @NotNull ModelIndexSet<GridRow> getSelectedRows() {
    return ViewIndexSet.forRows(myGrid, isTransposed() ? myColumnModel.getSelectedColumns() : selectedRows())
      .toModel(myGrid);
  }

  @Override
  public @NotNull ModelIndex<GridRow> getLeadSelectionRow() {
    int idx = isTransposed()
              ? myColumnModel.getSelectionModel().getLeadSelectionIndex()
              : myRowSelection.getLeadSelectionIndex();
    return ViewIndex.forRow(myGrid, idx).toModel(myGrid);
  }

  @Override
  public @NotNull ModelIndex<GridColumn> getSelectedColumn() {
    int idx = isTransposed() ? selectedRow() : myColumnModel.getSelectionModel().getMinSelectionIndex();
    return ViewIndex.forColumn(myGrid, idx).toModel(myGrid);
  }

  @Override
  public @NotNull ModelIndex<GridColumn> getLeadSelectionColumn() {
    int idx = isTransposed()
              ? myRowSelection.getLeadSelectionIndex()
              : myColumnModel.getSelectionModel().getLeadSelectionIndex();
    return ViewIndex.forColumn(myGrid, idx).toModel(myGrid);
  }

  @Override
  public @NotNull ModelIndexSet<GridColumn> getSelectedColumns() {
    return ViewIndexSet.forColumns(myGrid, isTransposed() ? selectedRows() : myColumnModel.getSelectedColumns())
      .toModel(myGrid);
  }

  @Override
  public void selectWholeRow() {
    myGrid.getAutoscrollLocker().runWithLock(() -> {
      myGrid.getHiddenColumnSelectionHolder().setWholeRowSelected(!isTransposed());
      setColumnSelectionInterval(myColumnModel.getColumnCount() - 1, 0);
    });
  }

  @Override
  public void selectWholeColumn() {
    myGrid.getAutoscrollLocker().runWithLock(() -> setRowSelectionInterval(rowCount() - 1, 0));
  }

  @Override
  public void clearSelection() {
    myRowSelection.clearSelection();
    myColumnModel.getSelectionModel().clearSelection();
  }

  /** Replaces the selection of the data's columns, or of its rows, with {@code viewIndices}, on the axis that shows them. */
  private void setSelection(boolean dataColumns, int[] viewIndices) {
    if (isTransposed() == dataColumns) {
      setSelection(myRowSelection, false, rowCount() - 1, viewIndices);
    }
    else {
      setSelection(myColumnModel.getSelectionModel(), false, myColumnModel.getColumnCount() - 1, viewIndices);
    }
  }

  @Override
  public void addRowSelection(@NotNull ModelIndexSet<GridRow> selection) {
    setSelection(myRowSelection, true, rowCount() - 1, selection.asArray());
  }

  @Override
  public int selectedViewRowsCount() {
    return isTransposed() ? getSelectedColumns().size() : getSelectedRows().size();
  }

  @Override
  public int selectedViewColumnsCount() {
    return isTransposed() ? getSelectedRows().size() : getSelectedColumns().size();
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
      normalized[count++] = clamp(index, maxSelectionIdx);
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
    idx0 = clamp(idx0, maxSelectionIdx);
    idx1 = clamp(idx1, maxSelectionIdx);
    if (add) {
      selectionModel.addSelectionInterval(idx0, idx1);
    }
    else {
      selectionModel.setSelectionInterval(idx0, idx1);
    }
  }

  private static int clamp(int idx, int maxIdx) {
    return Math.max(0, Math.min(idx, maxIdx));
  }

  private static int[] convert(int count, IntUnaryOperator function, int[] indices) {
    return Arrays.stream(indices)
      .filter(index -> index < count && index >= 0)
      .map(function)
      .filter(index -> index != -1)
      .toArray();
  }

  @Override
  public @NotNull GridSelection<GridRow, GridColumn> fit(@NotNull GridSelection<GridRow, GridColumn> selection) {
    int rowCount = isTransposed() ? myColumnModel.getColumnCount() : rowCount();
    int columnCount = isTransposed() ? rowCount() : myColumnModel.getColumnCount();
    ModelIndexSet<GridRow> rows = ModelIndexSet.forRows(myGrid, fit(selection.getSelectedRows().asArray(), rowCount));
    ModelIndexSet<GridColumn> columns = ModelIndexSet.forColumns(myGrid, fit(selection.getSelectedColumns().asArray(), columnCount));
    return GridSelectionImpl.withSelection(selection, rows, columns);
  }

  private static int[] fit(int @NotNull [] indices, int count) {
    ArrayList<Integer> newIndices = new ArrayList<>();
    for (int index : indices) {
      if (index < count) newIndices.add(index);
    }
    return ArrayUtil.toIntArray(newIndices);
  }

  /** Puts {@code selection} back, its lead and anchor too, as one change that the grid's selection listeners hear of once. */
  @Override
  public void restore(@NotNull GridSelection<GridRow, GridColumn> selection) {
    myEvents.runAsOneChange(() -> {
      clearSelection();
      SelectionModel<GridRow, GridColumn> selectionModel = myGrid.getSelectionModel();
      selectionModel.setSelection(selection.getSelectedRows(), selection.getSelectedColumns());

      // Re-adding the rows and the columns put each lead on the last index of its interval, and anything following a lead jumps there.
      restoreLeadAndAnchor(dataRowSelection(), toViewRow(selection.getCurrentRow()), toViewRow(selection.getRangeStartRow()));
      restoreLeadAndAnchor(dataColumnSelection(), toViewColumn(selection.getCurrentColumn()),
                           toViewColumn(selection.getRangeStartColumn()));
      myEvents.requestNotification();
    });
  }

  /** Puts the lead and the anchor back without changing the selection; a lead of -1 leaves both as they are. */
  static void restoreLeadAndAnchor(@NotNull ListSelectionModel model, int lead, int anchor) {
    if (!(model instanceof DefaultListSelectionModel selection) || lead < 0) return;
    if (anchor >= 0) selection.setAnchorSelectionIndex(anchor);
    selection.moveLeadSelectionIndex(lead); // moves the lead without changing the selection
  }
}
