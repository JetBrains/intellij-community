package com.intellij.database.run.ui.grid.selection;

import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.grid.editors.GridCellEditorHelper;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalInt;

public class GridSelectionTrackerImpl implements GridSelectionTracker {
  private final Stack<ModelIndex<GridRow>> myRows;
  private final DataGrid myGrid;

  private boolean myAdjusting;

  public GridSelectionTrackerImpl(@NotNull DataGrid grid, @NotNull ResultView view) {
    myGrid = grid;
    myRows = new Stack<>();
    myGrid.addDataGridListener(new DataGridListener() {
      @Override
      public void onSelectionChanged(DataGrid dataGrid) {
        if (myAdjusting) return;
        myRows.clear();
      }
    }, view);
  }

  @Override
  public void performOperation(@NotNull GridSelectionTracker.Operation operation) {
    if (!checkAndFillStack(operation)) return;
    operation.perform(this);
    updateSelection();
  }

  @Override
  public boolean canPerformOperation(@NotNull GridSelectionTracker.Operation operation) {
    return operation.checkStackSize(myRows.size()) ||
           operation.checkSelectedRowsCount(myGrid.getSelectionModel().getSelectedRowCount()) &&
           operation.checkSelectedColumnsCount(myGrid.getSelectionModel().getSelectedColumnCount()) &&
           checkRowEquality();
  }

  private boolean checkAndFillStack(@NotNull GridSelectionTracker.Operation operation) {
    if (!canPerformOperation(operation)) return false;
    if (myRows.isEmpty()) myRows.addAll(myGrid.getSelectionModel().getSelectedRows().asList());
    return true;
  }

  private void updateSelection() {
    run(() -> myGrid.getSelectionModel().setRowSelection(getRows(), false));
  }

  private @NotNull ModelIndexSet<GridRow> getRows() {
    int[] rows = myRows.stream()
      .filter(idx -> idx.isValid(myGrid))
      .mapToInt(ModelIndex::asInteger)
      .toArray();
    return ModelIndexSet.forRows(myGrid, rows);
  }

  private @Nullable ModelIndex<GridRow> findNext(@NotNull ViewIndex<GridRow> stopRow) {
    ViewIndex<GridRow> startRow = getLastRow();
    ViewIndex<GridRow> next = ViewIndex.forRow(myGrid, next(startRow.asInteger()));
    ModelIndex<GridRow> startRowModelIdx = startRow.toModel(myGrid);
    while (next.asInteger() != startRow.asInteger()) {
      if (stopRow.asInteger() == next.asInteger()) return null;
      ModelIndex<GridRow> nextModelIdx = next.toModel(myGrid);
      boolean isValid = next.isValid(myGrid);
      boolean isNotSelectedRow = !myGrid.getSelectionModel().isSelectedRow(nextModelIdx);
      boolean isNotInStack = !myRows.contains(nextModelIdx);
      boolean valuesAreEqual = rowEquals(startRowModelIdx, nextModelIdx);
      if (isNotInStack && isValid && isNotSelectedRow && valuesAreEqual) return nextModelIdx;
      next = ViewIndex.forRow(myGrid, next(next.asInteger()));
    }
    return null;
  }

  private @NotNull ViewIndex<GridRow> getLastRow() {
    if (!myRows.isEmpty()) return myRows.peek().toView(myGrid);
    ViewIndexSet<GridRow> rows = myGrid.getSelectionModel().getSelectedRows().toView(myGrid);
    OptionalInt max = rows.asList().stream()
      .mapToInt(ViewIndex::asInteger)
      .max();
    if (max.isEmpty()) return ViewIndex.forRow(myGrid, 0);
    return ViewIndex.forRow(myGrid, max.getAsInt());
  }

  private boolean checkRowEquality() {
    ModelIndexSet<GridRow> rows = myGrid.getSelectionModel().getSelectedRows();
    ModelIndex<GridRow> previous = rows.first();
    for (ModelIndex<GridRow> rowIdx : rows.asIterable()) {
      if (!rowEquals(rowIdx, previous)) return false;
    }
    return true;
  }

  private boolean rowEquals(@NotNull ModelIndex<GridRow> first, @NotNull ModelIndex<GridRow> second) {
    GridModel<GridRow, GridColumn> model = myGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
    ModelIndexSet<GridColumn> columns = myGrid.getSelectionModel().getSelectedColumns();
    for (ModelIndex<GridColumn> colIdx : columns.asIterable()) {
      if (!GridCellEditorHelper.get(myGrid).areValuesEqual(model.getValueAt(first, colIdx), model.getValueAt(second, colIdx), myGrid)) return false;
    }
    return true;
  }

  private int next(int current) {
    return ++current >= myGrid.getVisibleRowsCount() ? 0 : current;
  }

  private void run(@NotNull Runnable runnable) {
    myAdjusting = true;
    try {
      runnable.run();
    }
    finally {
      myAdjusting = false;
    }
  }

  public enum OperationImpl implements Operation {
    SELECT_NEXT(1, 1, 1) {
      @Override
      public boolean perform(@NotNull GridSelectionTracker tracker) {
        GridSelectionTrackerImpl t = (GridSelectionTrackerImpl)tracker;
        ModelIndex<GridRow> next = t.findNext(t.getLastRow());
        if (next == null) return false;
        t.myRows.add(next);
        return true;
      }
    },
    SELECT_ALL(1, 1, 1) {
      @Override
      public boolean perform(@NotNull GridSelectionTracker tracker) {
        GridSelectionTrackerImpl t = (GridSelectionTrackerImpl)tracker;
        ModelIndex<GridRow> next;
        int rowCount = t.myRows.size();
        ViewIndex<GridRow> row = t.getLastRow();
        while ((next = t.findNext(row)) != null) {
          t.myRows.add(next);
        }
        return rowCount != t.myRows.size();
      }
    },
    UNSELECT_PREVIOUS(2, 1, 2) {
      @Override
      public boolean perform(@NotNull GridSelectionTracker tracker) {
        GridSelectionTrackerImpl t = (GridSelectionTrackerImpl)tracker;
        t.myRows.pop();
        return true;
      }
    };

    private final int myMinStackSize;
    private final int myMinSelectedColumns;
    private final int myMinSelectedRows;

    OperationImpl(int minStackSize, int minSelectedColumns, int minSelectedRows) {
      myMinStackSize = minStackSize;
      myMinSelectedColumns = minSelectedColumns;
      myMinSelectedRows = minSelectedRows;
    }

    @Override
    public boolean checkStackSize(int size) {
      return size >= myMinStackSize;
    }

    @Override
    public boolean checkSelectedColumnsCount(int count) {
      return count >= myMinSelectedColumns;
    }

    @Override
    public boolean checkSelectedRowsCount(int count) {
      return count >= myMinSelectedRows;
    }
  }
}
