package com.intellij.database.run.ui.grid;

import com.intellij.database.datagrid.*;
import com.intellij.database.datagrid.mutating.CellMutation;
import com.intellij.database.run.ui.DataGridRequestPlace;
import com.intellij.database.run.ui.GridDataSupport;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class GridDataSupportImpl implements GridDataSupport {
  private final GridMutator<GridRow, GridColumn> myMutator;
  private final DataGrid myGrid;

  public GridDataSupportImpl(@NotNull DataGrid grid, @Nullable GridMutator<GridRow, GridColumn> mutator) {
    myMutator = mutator;
    myGrid = grid;
  }

  @Override
  public void revert(@NotNull ModelIndexSet<GridRow> rows, @NotNull ModelIndexSet<GridColumn> columns) {
    GridMutator.DatabaseMutator<GridRow, GridColumn> mutator = getDatabaseMutator();
    if (mutator == null) return;
    GridRequestSource source = new GridRequestSource(
      new DataGridRequestPlace(myGrid, rows, columns));
    GridSelection<GridRow, GridColumn> selection = myGrid.getSelectionModel().store();
    mutator.revert(source, rows, columns);
    myGrid.getAutoscrollLocker().runWithLock(() -> {
      SelectionModel<GridRow, GridColumn> selectionModel = myGrid.getSelectionModel();
      selectionModel.restore(selectionModel.fit(selection));
    });
  }

  private @Nullable GridMutator.DatabaseMutator<GridRow, GridColumn> getDatabaseMutator() {
    //noinspection unchecked
    return ObjectUtils.tryCast(myMutator, GridMutator.DatabaseMutator.class);
  }

  private @Nullable GridMutator.RowsMutator<GridRow, GridColumn> getRowsMutator() {
    //noinspection unchecked
    return ObjectUtils.tryCast(myMutator, GridMutator.RowsMutator.class);
  }

  private @Nullable GridMutator.ColumnsMutator<GridRow, GridColumn> getColumnsMutator() {
    //noinspection unchecked
    return ObjectUtils.tryCast(myMutator, GridMutator.ColumnsMutator.class);
  }

  @Override
  public boolean isDeletedRows(@NotNull ModelIndexSet<GridRow> rows) {
    GridMutator.RowsMutator<GridRow, GridColumn> mutator = getRowsMutator();
    return mutator != null && mutator.isDeletedRows(rows);
  }

  @Override
  public boolean isModified(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    GridMutator.DatabaseMutator<GridRow, GridColumn> mutator = getDatabaseMutator();
    if (mutator == null) return false;
    return mutator.getMutationType(row, column) == MutationType.MODIFY;
  }

  @Override
  public boolean isDeletedColumn(@NotNull ModelIndex<GridColumn> column) {
    GridMutator.ColumnsMutator<GridRow, GridColumn> mutator = getColumnsMutator();
    return mutator != null && mutator.isDeletedColumn(column);
  }

  @Override
  public boolean isInsertedColumn(@NotNull ModelIndex<GridColumn> column) {
    GridMutator.ColumnsMutator<GridRow, GridColumn> mutator = getColumnsMutator();
    return mutator != null && mutator.isInsertedColumn(column);
  }

  @Override
  public int getInsertedColumnsCount() {
    GridMutator.ColumnsMutator<GridRow, GridColumn> mutator = getColumnsMutator();
    return mutator == null ? 0 : mutator.getInsertedColumnsCount();
  }

  @Override
  public boolean hasPendingChanges() {
    return hasMutator() && Objects.requireNonNull(myMutator).hasPendingChanges();
  }

  @Override
  public boolean hasUnparsedValues() {
    return hasMutator() && Objects.requireNonNull(myMutator).hasUnparsedValues();
  }

  @Override
  public boolean hasMutator() {
    return myMutator != null;
  }

  @Override
  public boolean hasRowMutator() {
    return getRowsMutator() != null;
  }

  @Override
  public boolean canRevert() {
    return getDatabaseMutator() != null;
  }

  @Override
  public boolean isSubmitImmediately() {
    return myMutator != null && myMutator.isUpdateImmediately();
  }

  @Override
  public void finishBuildingAndApply(@NotNull List<CellMutation.Builder> builders) {
    if (myMutator == null) return;
    List<CellMutation> mutations = ContainerUtil.map(builders, CellMutation.Builder::build);
    ModelIndexSet<GridRow> rows = ModelIndexSet.forRows(myGrid, uniqueIndices(mutations, CellMutation::getRow));
    ModelIndexSet<GridColumn> columns = ModelIndexSet.forColumns(myGrid, uniqueIndices(mutations, CellMutation::getColumn));
    myMutator.mutate(new GridRequestSource(new DataGridRequestPlace(myGrid, rows, columns)), mutations, true);
  }

  private static int[] uniqueIndices(@NotNull List<CellMutation> mutations, @NotNull Function<CellMutation, ModelIndex<?>> function) {
    return mutations.stream()
      .mapToInt(mutation -> function.fun(mutation).asInteger())
      .distinct()
      .toArray();
  }
}
