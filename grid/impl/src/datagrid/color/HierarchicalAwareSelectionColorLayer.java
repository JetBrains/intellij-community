package com.intellij.database.datagrid.color;

import com.intellij.database.datagrid.*;
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn;
import com.intellij.database.run.ui.DataAccessType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static com.intellij.database.datagrid.GridUtil.getLastAncestorWithSelectedDirectLeaf;
import static com.intellij.database.datagrid.color.SelectionColorLayer.*;
import static java.lang.Integer.max;

public class HierarchicalAwareSelectionColorLayer implements ColorLayer {
  private final SelectionColorLayer myDelegate;

  public HierarchicalAwareSelectionColorLayer(@NotNull SelectionColorLayer delegate) {
    myDelegate = delegate;
  }

  @Override
  public @Nullable Color getCellBackground(@NotNull ModelIndex<GridRow> row,
                                           @NotNull ModelIndex<GridColumn> column,
                                           @NotNull DataGrid grid,
                                           @Nullable Color color) {
    boolean isRowSelected = grid.getSelectionModel().isSelectedRow(row);
    if (isRowBgPaintedByTable(row, column, grid, isRowSelected)) {
      return color;
    }

    GridColumn c = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(column);
    if (!(c instanceof HierarchicalGridColumn hierarchicalGridColumn)) {
      return myDelegate.getCellBackground(row, column, grid, color);
    }

    if (GridUtil.getClosestAncestorWithSelectedDirectLeaf(grid, hierarchicalGridColumn) == null) {
      return color;
    }

    return getSelectedRowColor(grid, color);
  }

  @Override
  public @Nullable Color getRowHeaderBackground(@NotNull ModelIndex<GridRow> row, @NotNull DataGrid grid, @Nullable Color color) {
    return myDelegate.getRowHeaderBackground(row, grid, color);
  }

  @Override
  public @Nullable Color getColumnHeaderBackground(@NotNull ModelIndex<GridColumn> column, @NotNull DataGrid grid, @Nullable Color color) {
    return myDelegate.getColumnHeaderBackground(column, grid, color);
  }

  @Override
  public @NotNull Color getRowHeaderForeground(@NotNull ModelIndex<GridRow> row, @NotNull DataGrid grid, @Nullable Color color) {
    return myDelegate.getRowHeaderForeground(row, grid, color);
  }

  @Override
  public @NotNull Color getColumnHeaderForeground(@NotNull ModelIndex<GridColumn> column, @NotNull DataGrid grid, @Nullable Color color) {
    return myDelegate.getColumnHeaderForeground(column, grid, color);
  }

  @Override
  public int getPriority() {
    return 3;
  }

  /**
   * If the column is a leaf and top-level, it is only highlighted if it is selected.
   * If the column is part of the column’s subtree, the process follows these steps:
   * 1. Find the closest ancestor column that has a selected child-leaf column.
   * 2. Determine the highest ancestor from the result of step 1.
   * 3. Based on the depth of the ancestors and the selection status of the column, calculate the start and end line positions.
   * <p>
   * Note:
   * - The start line is the depth of the highest ancestor with selected child leaf.
   * - The end line is dictated by the column's selection:
   * - If the column itself is selected, it's set to index of the last header line.
   * - Otherwise, it aligns with the header line index of the column's immediate ancestor.
   */
  @Override
  public @Nullable Color getColumnHeaderBackground(@NotNull ModelIndex<GridColumn> column,
                                                   int headerLine,
                                                   @NotNull DataGrid grid,
                                                   @Nullable Color color) {
    GridColumn c = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(column);
    if (!(c instanceof HierarchicalGridColumn hierarchicalColumn)) {
      return myDelegate.getColumnHeaderBackground(column, headerLine, grid, color);
    }

    boolean isSelected = grid.getSelectionModel().isSelectedColumn(column);
    if (hierarchicalColumn.isTopLevelColumn() && hierarchicalColumn.isLeaf() && isSelected) {
      return getSelectedColumnColor(grid, color);
    }

    HierarchicalGridColumn closestAncestor = GridUtil.getClosestAncestorWithSelectedDirectLeaf(grid, hierarchicalColumn);
    if (closestAncestor == null) return myDelegate.getColumnHeaderBackground(column, grid, color);

    HierarchicalGridColumn highestAncestor = getLastAncestorWithSelectedDirectLeaf(grid, closestAncestor);
    assert highestAncestor != null;

    int startLine = max(highestAncestor.getPathFromRoot().length - 1, 0);
    int endLine = max(closestAncestor.getPathFromRoot().length - 1, 0);

    if (shouldHighlight(isSelected, headerLine, startLine, endLine)) {
      return getSelectedColumnColor(grid, color);
    }

    return myDelegate.getHeaderColor(color);
  }

  private static boolean shouldHighlight(boolean isSelected, int headerLine, int startLine, int endLine) {
    return (isSelected && headerLine >= startLine) || (!isSelected && headerLine >= startLine && headerLine <= endLine);
  }
}
