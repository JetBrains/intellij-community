package com.intellij.database.run.ui.grid;

import com.intellij.database.datagrid.*;
import com.intellij.database.datagrid.mutating.MutationData;
import com.intellij.database.run.ReservedCellValue;
import com.intellij.openapi.Disposable;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.util.containers.ContainerUtil.emptyList;

public class GridMutationModel implements GridModel<GridRow, GridColumn> {
  private final GridModel<GridRow, GridColumn> myModel;
  private final Map<ModelIndex<GridRow>, GridRow> myCache;
  private final GridDataHookUp<GridRow, GridColumn> myHookUp;
  @SuppressWarnings("rawtypes")
  private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);

  public GridMutationModel(@NotNull GridDataHookUp<GridRow, GridColumn> hookUp) {
    myHookUp = hookUp;
    myModel = myHookUp.getDataModel();
    myCache = new ConcurrentHashMap<>();
  }

  @Override
  public @NotNull List<GridColumn> getColumns() {
    return getColumnIndicesInner().map(this::wrapColumn).toList();
  }

  @Override
  public @NotNull JBIterable<GridColumn> getColumnsAsIterable() {
    return getColumnIndicesInner().map(this::wrapColumn);
  }

  @Override
  public @NotNull List<GridColumn> getColumns(@NotNull ModelIndexSet<GridColumn> columnsIdxs) {
    return columnsIdxs.asIterable().filter(this::isValidColumnIdx).map(this::wrapColumn).toList(); // todo remove
  }

  @Override
  public @NotNull JBIterable<GridColumn> getColumnsAsIterable(@NotNull ModelIndexSet<GridColumn> columns) {
    return columns.asIterable().filter(this::isValidColumnIdx).map(this::wrapColumn);
  }

  @Override
  public @Nullable GridColumn getColumn(@NotNull ModelIndex<GridColumn> columnIdx) {
    return !isValidColumnIdx(columnIdx) ? null : wrapColumn(columnIdx);
  }

  @Override
  public @NotNull List<GridRow> getRows(@NotNull ModelIndexSet<GridRow> rows) {
    return rows.asIterable().filter(this::isValidRowIdx).map(this::wrapRow).toList();
  }

  @Override
  public @Nullable Object getValueAt(ModelIndex<GridRow> row, ModelIndex<GridColumn> column) {
    return getValueAt(row, column, myHookUp.getMutator(), myModel);
  }

  public static @Nullable Object getValueAt(ModelIndex<GridRow> row, ModelIndex<GridColumn> column,
                                            @Nullable GridMutator<GridRow, GridColumn> mutator,
                                            @NotNull GridModel<GridRow, GridColumn> model) {
    //noinspection unchecked
    GridMutator.DatabaseMutator<GridRow, GridColumn> databaseMutator = ObjectUtils.tryCast(mutator, GridMutator.DatabaseMutator.class);
    //noinspection unchecked
    GridMutator.ColumnsMutator<GridRow, GridColumn> columnsMutator = ObjectUtils.tryCast(mutator, GridMutator.ColumnsMutator.class);
    MutationData value = databaseMutator == null ? null : databaseMutator.getMutation(row, column);
    return value != null
           ? value.getValue()
           : row.isValid(model) && column.isValid(model)
             ? model.getValueAt(row, column)
             : columnsMutator != null && (columnsMutator.isDeletedColumn(column) || columnsMutator.isInsertedColumn(column))
               ? ReservedCellValue.UNSET
               : null
      ;
  }

  @Override
  public boolean allValuesEqualTo(@NotNull ModelIndexSet<GridRow> rowIndices,
                                  @NotNull ModelIndexSet<GridColumn> columnIndices,
                                  Object what) {
    return myModel.allValuesEqualTo(rowIndices, columnIndices, what) &&
           (getDatabaseMutator() == null || !getDatabaseMutator().hasMutatedRows(rowIndices, columnIndices));
  }

  @Override
  public @Nullable GridRow getRow(@NotNull ModelIndex<GridRow> row) {
    return !isValidRowIdx(row) ? null : wrapRow(row);
  }

  @Override
  public @NotNull List<GridRow> getRows() {
    return getRowIndicesInner().map(this::wrapRow).toList();
  }

  private @Nullable GridRow wrapRow(@NotNull ModelIndex<GridRow> rowIdx) {
    GridMutator.DatabaseMutator<GridRow, GridColumn> mutator = getDatabaseMutator();
    MutationType type = mutator == null ? null : mutator.getMutationType(rowIdx);
    return type == MutationType.MODIFY || type == MutationType.INSERT
           ? myCache.computeIfAbsent(rowIdx, r -> new MutationRow(rowIdx, new Object[getColumnCount()], mutator, myModel))
           : myModel.getRow(rowIdx);
  }

  private @Nullable GridColumn wrapColumn(@NotNull ModelIndex<GridColumn> columnIdx) {
    GridMutator.ColumnsMutator<GridRow, GridColumn> mutator = getColumnsMutator();
    GridColumn column = mutator == null ? null : mutator.getInsertedColumn(columnIdx);
    return column != null ? column : myModel.getColumn(columnIdx);
  }

  @Override
  public @NotNull ModelIndexSet<GridColumn> getColumnIndices() {
    return ModelIndexSet.forColumns(myModel, getColumnIndicesInner());
  }

  @Override
  public @NotNull ModelIndexSet<GridRow> getRowIndices() {
    return ModelIndexSet.forRows(myModel, getRowIndicesInner());
  }

  private JBIterable<ModelIndex<GridRow>> getRowIndicesInner() {
    GridMutator.RowsMutator<GridRow, GridColumn> mutator = getRowsMutator();
    return myModel.getRowIndices().asIterable().append(mutator == null ? emptyList() : mutator.getInsertedRows());
  }

  private JBIterable<ModelIndex<GridColumn>> getColumnIndicesInner() {
    GridMutator.ColumnsMutator<GridRow, GridColumn> mutator = getColumnsMutator();
    return myModel.getColumnIndices().asIterable().append(mutator == null ? emptyList() : mutator.getInsertedColumns());
  }

  @Override
  public int getColumnCount() {
    GridMutator.ColumnsMutator<GridRow, GridColumn> mutator = getColumnsMutator();
    return myModel.getColumnCount() + (mutator == null ? 0 : mutator.getInsertedColumnsCount());
  }

  @Override
  public int getRowCount() {
    GridMutator.RowsMutator<GridRow, GridColumn> mutator = getRowsMutator();
    return myModel.getRowCount() + (mutator == null ? 0 : mutator.getInsertedRowsCount());
  }

  @Override
  public boolean isValidRowIdx(@NotNull ModelIndex<GridRow> rowIdx) {
    GridMutator.RowsMutator<GridRow, GridColumn> mutator = getRowsMutator();
    return myModel.isValidRowIdx(rowIdx) || mutator != null && mutator.isInsertedRow(rowIdx);
  }

  @Override
  public boolean isValidColumnIdx(@NotNull ModelIndex<GridColumn> columnIdx) {
    GridMutator.ColumnsMutator<GridRow, GridColumn> mutator = getColumnsMutator();
    return myModel.isValidColumnIdx(columnIdx) || mutator != null && mutator.isInsertedColumn(columnIdx);
  }

  @Override
  public boolean isUpdatingNow() {
    return myModel.isUpdatingNow();
  }

  @Override
  public void addListener(@NotNull Listener<GridRow, GridColumn> l, @NotNull Disposable disposable) {
    myDispatcher.addListener(l, disposable);
  }

  @Override
  public boolean hasListeners() {
    return myDispatcher.hasListeners();
  }

  @Override
  public List<GridColumn> getAllColumnsForExtraction(int... selection) {
    return myModel.getAllColumnsForExtraction(selection);
  }

  private @Nullable GridMutator.DatabaseMutator<GridRow, GridColumn> getDatabaseMutator() {
    //noinspection unchecked
    return ObjectUtils.tryCast(myHookUp.getMutator(), GridMutator.DatabaseMutator.class);
  }

  private @Nullable GridMutator.RowsMutator<GridRow, GridColumn> getRowsMutator() {
    //noinspection unchecked
    return ObjectUtils.tryCast(myHookUp.getMutator(), GridMutator.RowsMutator.class);
  }

  private @Nullable GridMutator.ColumnsMutator<GridRow, GridColumn> getColumnsMutator() {
    //noinspection unchecked
    return ObjectUtils.tryCast(myHookUp.getMutator(), GridMutator.ColumnsMutator.class);
  }

  public void afterLastRowAdded() {
    myDispatcher.getMulticaster().afterLastRowAdded();
  }

  public void notifyCellsUpdated(@NotNull ModelIndexSet<GridRow> rows,
                                 @NotNull ModelIndexSet<GridColumn> columns,
                                 @Nullable GridRequestSource.RequestPlace place) {
    if (rows.size() == 0 || columns.size() == 0) return;
    //noinspection unchecked
    myDispatcher.getMulticaster().cellsUpdated(rows, columns, place);
  }

  public void notifyRowsAdded(@NotNull ModelIndexSet<GridRow> rows) {
    if (rows.size() == 0) return;
    //noinspection unchecked
    myDispatcher.getMulticaster().rowsAdded(rows);
  }

  public void notifyRowsRemoved(ModelIndexSet<GridRow> rows) {
    if (rows.size() == 0) return;
    //noinspection unchecked
    myDispatcher.getMulticaster().rowsRemoved(rows);
  }

  public void notifyColumnsAdded(@NotNull ModelIndexSet<GridColumn> columns) {
    if (columns.size() == 0) return;
    //noinspection unchecked
    myDispatcher.getMulticaster().columnsAdded(columns);
  }

  public void notifyColumnsRemoved(@NotNull ModelIndexSet<GridColumn> columns) {
    if (columns.size() == 0) return;
    //noinspection unchecked
    myDispatcher.getMulticaster().columnsRemoved(columns);
  }

  @Override
  public @Nullable HierarchicalReader getHierarchicalReader() {
    return myModel.getHierarchicalReader();
  }
}
