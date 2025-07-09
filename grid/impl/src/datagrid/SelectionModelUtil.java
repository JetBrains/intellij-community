package com.intellij.database.datagrid;

import com.intellij.database.run.ui.grid.selection.GridSelectionTracker;
import com.intellij.database.run.ui.grid.selection.GridSelectionTrackerImpl;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public final class SelectionModelUtil {
  public static final String SELECTION_MODEL_KEY = "SelectionModel";

  public static @NotNull <Row, Column> SelectionModel<Row, Column> get(@NotNull DataGrid grid, @NotNull ResultView resultView) {
    SelectionModel<?, ?> manager =
      ObjectUtils.tryCast(resultView.getComponent().getClientProperty(SELECTION_MODEL_KEY), SelectionModel.class);
    if (manager == null) {
      manager = new DummySelectionModel(grid, resultView);
      resultView.getComponent().putClientProperty(SELECTION_MODEL_KEY, manager);
    }
    //noinspection unchecked
    return (SelectionModel<Row, Column>)manager;
  }

  public static class DummySelectionModel implements SelectionModel<GridRow, GridColumn> {
    private final DataGrid myGrid;
    private final GridSelectionTracker myTracker;


    DummySelectionModel(@NotNull DataGrid grid, @NotNull ResultView view) {
      myGrid = grid;
      myTracker = new GridSelectionTrackerImpl(grid, view);
    }

    @Override
    public @NotNull GridSelection<GridRow, GridColumn> store() {
      return new GridSelectionImpl(ModelIndexSet.forRows(myGrid), ModelIndexSet.forColumns(myGrid));
    }

    @Override
    public void restore(@NotNull GridSelection<GridRow, GridColumn> selection) {
    }

    @Override
    public @NotNull GridSelection<GridRow, GridColumn> fit(@NotNull GridSelection<GridRow, GridColumn> selection) {
      return new GridSelectionImpl(selection.getSelectedRows(), selection.getSelectedColumns());
    }

    @Override
    public @NotNull GridSelectionTracker getTracker() {
      return myTracker;
    }

    @Override
    public void setRowSelection(@NotNull ModelIndexSet<GridRow> selection, boolean selectAtLeastOneCell) {
    }

    @Override
    public void setSelection(@NotNull ModelIndexSet<GridRow> rows, @NotNull ModelIndexSet<GridColumn> columns) {
    }

    @Override
    public void setSelection(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    }

    @Override
    public void setRowSelection(@NotNull ModelIndex<GridRow> selection, boolean selectAtLeastOneCell) {
    }

    @Override
    public void addRowSelection(@NotNull ModelIndexSet<GridRow> selection) {
    }

    @Override
    public void setColumnSelection(@NotNull ModelIndexSet<GridColumn> selection, boolean selectAtLeastOneCell) {
    }

    @Override
    public void setColumnSelection(@NotNull ModelIndex<GridColumn> selection, boolean selectAtLeastOneCell) {
    }

    @Override
    public boolean isSelectionEmpty() {
      return true;
    }

    @Override
    public boolean isSelected(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
      return false;
    }

    @Override
    public boolean isSelected(@NotNull ViewIndex<GridRow> row, @NotNull ViewIndex<GridColumn> column) {
      return false;
    }

    @Override
    public boolean isSelectedColumn(@NotNull ModelIndex<GridColumn> column) {
      return false;
    }

    @Override
    public boolean isSelectedRow(@NotNull ModelIndex<GridRow> row) {
      return false;
    }

    @Override
    public int getSelectedRowCount() {
      return 0;
    }

    @Override
    public int getSelectedColumnCount() {
      return 0;
    }

    @Override
    public void selectWholeRow() {
    }

    @Override
    public void selectWholeColumn() {
    }

    @Override
    public void clearSelection() {
    }

    @Override
    public @NotNull ModelIndex<GridRow> getSelectedRow() {
      return ModelIndex.forRow(myGrid, -1);
    }

    @Override
    public @NotNull ModelIndexSet<GridRow> getSelectedRows() {
      return ModelIndexSet.forRows(myGrid);
    }

    @Override
    public @NotNull ModelIndex<GridColumn> getSelectedColumn() {
      return ModelIndex.forColumn(myGrid, -1);
    }

    @Override
    public @NotNull ModelIndexSet<GridColumn> getSelectedColumns() {
      return ModelIndexSet.forColumns(myGrid);
    }
  }
}
