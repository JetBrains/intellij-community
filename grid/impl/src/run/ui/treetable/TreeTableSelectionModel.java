package com.intellij.database.run.ui.treetable;

import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.grid.selection.GridSelectionTracker;
import com.intellij.database.run.ui.grid.selection.GridSelectionTrackerImpl;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Liudmila Kornilova
 **/
class TreeTableSelectionModel implements SelectionModel<GridRow, GridColumn>, SelectionModelWithViewRows, SelectionModelWithViewColumns {
  private final DataGrid myGrid;
  private final TreeTableResultView myView;
  private final GridSelectionTrackerImpl myTracker;


  TreeTableSelectionModel(@NotNull DataGrid grid, @NotNull TreeTableResultView view) {
    myGrid = grid;
    myView = view;
    myTracker = new GridSelectionTrackerImpl(grid, view);
    myView.getComponent().putClientProperty(SelectionModelUtil.SELECTION_MODEL_KEY, this);
  }

  @Override
  public @NotNull GridSelection<GridRow, GridColumn> store() {
    int[] viewRows = myView.getComponent().getTable().getSelectedRows();
    int[] modelRows = new int[viewRows.length];
    int[] modelColumns = new int[viewRows.length];
    for (int i = 0; i < viewRows.length; i++) {
      Pair<Integer, Integer> rowAndColumn = myGrid.getRawIndexConverter().rowAndColumn2Model().fun(viewRows[i], 0);
      modelRows[i] = rowAndColumn.first;
      modelColumns[i] = rowAndColumn.second;
    }
    return new GridSelectionImpl(ModelIndexSet.forRows(myGrid, modelRows), ModelIndexSet.forColumns(myGrid, modelColumns));
  }

  @Override
  public void restore(@NotNull GridSelection<GridRow, GridColumn> selection) {
    RawIndexConverter converter = myGrid.getRawIndexConverter();
    for (ModelIndex<GridRow> modelRow : selection.getSelectedRows().asIterable()) {
      for (ModelIndex<GridColumn> modelColumn : selection.getSelectedColumns().asIterable()) {
        Pair<Integer, Integer> rowAndColumn = converter.rowAndColumn2View().fun(modelRow.asInteger(), modelColumn.asInteger());
        int rowIdx = rowAndColumn.first;
        if (converter.isValidViewRowIdx(rowIdx)) {
          myView.getComponent().getTree().setSelectionRow(rowIdx);
        }
      }
    }
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
    myView.getComponent().getTree().clearSelection();
    addRowSelection(selection);
  }

  @Override
  public void setSelection(@NotNull ModelIndexSet<GridRow> rows, @NotNull ModelIndexSet<GridColumn> columns) {
    RawIndexConverter converter = myGrid.getRawIndexConverter();
    var selectedViewRows = new ArrayList<Integer>();
    for (ModelIndex<GridRow> rowIdx : rows.asIterable()) {
      for (ModelIndex<GridColumn> columnIdx : columns.asIterable()) {
        var rowAndColumn = converter.rowAndColumn2View().fun(rowIdx.asInteger(), columnIdx.asInteger());
        int viewRow = rowAndColumn.first;
        if (!converter.isValidViewRowIdx(viewRow)) {
          continue;
        }

        selectedViewRows.add(viewRow);
      }
    }

    myView.getComponent().getTree().setSelectionRows(selectedViewRows.stream().mapToInt(x -> x).toArray());
  }

  @Override
  public void setSelection(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    setSelection(ModelIndexSet.forRows(myGrid, row.value), ModelIndexSet.forColumns(myGrid, column.value));
  }

  @Override
  public void setRowSelection(@NotNull ModelIndex<GridRow> selection, boolean selectAtLeastOneCell) {
    setRowSelection(ModelIndexSet.forRows(myGrid, selection.value), selectAtLeastOneCell);
  }

  @Override
  public void addRowSelection(@NotNull ModelIndexSet<GridRow> selection) {
    for (ModelIndex<GridRow> rowIdx : selection.asIterable()) {
      ViewIndex<GridRow> viewIdx = rowIdx.toView(myGrid);
      myView.getComponent().getTree().addSelectionRow(viewIdx.asInteger());
    }
  }

  @Override
  public void setColumnSelection(@NotNull ModelIndexSet<GridColumn> selection, boolean selectAtLeastOneCell) {
    myView.getComponent().getTree().clearSelection();
    ModelIndexSet<GridRow> rows = getSelectedRows();
    if (rows.size() == 0 && selectAtLeastOneCell) {
      rows = ModelIndexSet.forRows(myGrid, 0);
    }
    setSelection(rows, selection);
  }

  @Override
  public void setColumnSelection(@NotNull ModelIndex<GridColumn> selection, boolean selectAtLeastOneCell) {
    setColumnSelection(ModelIndexSet.forColumns(myGrid, selection.value), selectAtLeastOneCell);
  }

  @Override
  public boolean isSelectionEmpty() {
    return myView.getComponent().getTree().isSelectionEmpty();
  }

  @Override
  public boolean isSelected(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    Pair<Integer, Integer> rowAndColumn = myView.getRawIndexConverter().rowAndColumn2View().fun(row.asInteger(), column.asInteger());
    return myView.getComponent().getTable().isRowSelected(rowAndColumn.first);
  }

  @Override
  public boolean isSelected(@NotNull ViewIndex<GridRow> row, @NotNull ViewIndex<GridColumn> column) {
    return myView.getComponent().getTable().isRowSelected(row.asInteger());
  }

  @Override
  public boolean isSelectedColumn(@NotNull ModelIndex<GridColumn> column) {
    int viewColumn = myView.getRawIndexConverter().column2View().applyAsInt(column.asInteger());
    return myView.getComponent().getTable().isColumnSelected(viewColumn);
  }

  @Override
  public boolean isSelectedRow(@NotNull ModelIndex<GridRow> row) {
    TreePath[] paths = myView.getComponent().getTree().getSelectionPaths();
    if (paths == null) return false;
    return ContainerUtil.find(paths, path ->
      ContainerUtil.find(path.getPath(), element ->
        element instanceof RowNode && ((RowNode)element).getRowIdx().equals(row)) != null) != null;
  }

  @Override
  public int getSelectedRowCount() {
    return getSelectedRows().size();
  }

  @Override
  public int getSelectedColumnCount() {
    return getSelectedColumns().size();
  }

  @Override
  public void selectWholeRow() {
    myGrid.getAutoscrollLocker().runWithLock(() -> {
      setRowSelection(getSelectedRows(), true);
    });
  }

  @Override
  public void selectWholeColumn() {
    myGrid.getAutoscrollLocker().runWithLock(() -> {
      ModelIndexSet<GridRow> rows = myGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getRowIndices();
      setSelection(rows, getSelectedColumns());
    });
  }

  @Override
  public void clearSelection() {
    myView.getComponent().getTree().clearSelection();
  }

  @Override
  public @NotNull ModelIndex<GridRow> getSelectedRow() {
    TreePath path = myView.getComponent().getTree().getSelectionPath();
    RowNode rowNode = findNode(path, RowNode.class);
    return rowNode == null ? ModelIndex.forRow(myGrid, -1) : rowNode.getRowIdx();
  }

  @Override
  public int selectedViewRowsCount() {
    return getSelectedRowCount();
  }

  @Override
  public int selectedViewColumnsCount() {
    return getSelectedColumnCount();
  }

  private static @Nullable <T> T findNode(@Nullable TreePath path, Class<? super T> aClass) {
    if (path == null) return null;
    //noinspection unchecked
    return (T)ContainerUtil.find(path.getPath(), element -> Conditions.instanceOf(aClass).value(element));
  }

  @Override
  public @NotNull ModelIndexSet<GridRow> getSelectedRows() {
    TreePath[] paths = myView.getComponent().getTree().getSelectionPaths();
    if (paths == null) return ModelIndexSet.forRows(myGrid);

    List<RowNode> rowNodes = ContainerUtil.mapNotNull(paths, path -> findNode(path, RowNode.class));
    List<Integer> rowIndices = ContainerUtil.map(rowNodes, rowNode -> rowNode.getRowIdx().asInteger());
    return ModelIndexSet.forRows(myGrid, ArrayUtil.toIntArray(rowIndices));
  }

  @Override
  public @NotNull ModelIndex<GridColumn> getSelectedColumn() {
    TreePath path = myView.getComponent().getTree().getSelectionPath();
    ColumnNode columnNode = findNode(path, ColumnNode.class);
    return columnNode == null ? ModelIndex.forColumn(myGrid, -1) : columnNode.getColumnIdx();
  }

  @Override
  public @NotNull ModelIndexSet<GridColumn> getSelectedColumns() {
    TreePath[] paths = myView.getComponent().getTree().getSelectionPaths();
    if (paths == null) return ModelIndexSet.forColumns(myGrid);

    List<ColumnNode> columnNodes = ContainerUtil.mapNotNull(paths, path -> findNode(path, ColumnNode.class));
    List<Integer> columnIndices = ContainerUtil.map(columnNodes, rowNode -> rowNode.getColumnIdx().asInteger());
    return ModelIndexSet.forColumns(myGrid, ArrayUtil.toIntArray(columnIndices));
  }
}
