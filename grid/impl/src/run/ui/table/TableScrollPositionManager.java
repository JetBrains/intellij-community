package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.run.ui.grid.GridScrollPositionManager;
import com.intellij.ui.TableCell;
import com.intellij.ui.TableUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.ListSelectionModel;
import java.awt.Point;
import java.awt.Rectangle;

/** Stores and restores the grid's scroll position, which is the main table's, and scrolls cells of the main table into view. */
class TableScrollPositionManager implements GridScrollPositionManager {
  private final GridTable myMainTable;
  /** The view's row selection, which both tables share. */
  private final ListSelectionModel myRowSelection;
  private final DataGrid myGrid;

  TableScrollPositionManager(@NotNull GridTable mainTable, @NotNull ListSelectionModel rowSelection, @NotNull DataGrid grid) {
    myMainTable = mainTable;
    myRowSelection = rowSelection;
    myGrid = grid;
  }

  @Override
  public GridScrollPosition store() {
    Rectangle visibleRect = myMainTable.getVisibleRect();
    Point p = visibleRect.getLocation();
    p.x += 1;
    p.y += 1;
    // At the very edge remember the first row or column, even when it is too narrow for the point to land in it.
    int row = visibleRect.y == 0 ? 0 : myMainTable.rowAtPoint(p);
    int column = visibleRect.x == 0 ? 0 : myMainTable.columnAtPoint(p);
    int modelRow = myMainTable.getRawIndexConverter().row2Model().applyAsInt(myMainTable.isTransposed() ? column : row);
    int modelColumn = myMainTable.getRawIndexConverter().column2Model().applyAsInt(myMainTable.isTransposed() ? row : column);
    return new GridScrollPosition(ModelIndex.forRow(myGrid, modelRow), ModelIndex.forColumn(myGrid, modelColumn));
  }

  @Override
  public void restore(@NotNull GridScrollPosition position) {
    TableCell cell = tableCell(position.myTopRowIdx, position.myLeftColumnIdx);
    Rectangle targetRect = myMainTable.getCellRect(cell.row, cell.column, true);
    Rectangle visibleRect = myMainTable.getVisibleRect();
    // A column that the pinned table shows has no place in the main table, so the main table keeps its horizontal scroll.
    if (!myMainTable.isTransposed() && cell.column < 0 && isShownInView(position.myLeftColumnIdx)) targetRect.x = visibleRect.x;
    targetRect.width = visibleRect.width;
    targetRect.height = visibleRect.height;
    myMainTable.scrollRectToVisible(targetRect);
  }

  @Override
  public void scrollSelectionToVisible() {
    // A selection only in pinned columns has no column in the main table to scroll to, only rows.
    if (!myMainTable.isTransposed() && myMainTable.getSelectedColumnCount() == 0 &&
        myGrid.getSelectionModel().getSelectedColumnCount() > 0) {
      scrollSelectedRowsToVisible();
    }
    else {
      TableUtil.scrollSelectionToVisible(myMainTable);
    }
  }

  @Override
  public void scrollCellToVisible(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    TableCell cell = tableCell(row, column);
    if (cell.row < 0 && cell.column < 0) return;
    Rectangle visibleRect = myMainTable.getVisibleRect();
    Rectangle targetRect = myMainTable.getCellRect(cell.row, cell.column, true);
    if (cell.row < 0) {
      targetRect.y = visibleRect.y;
      targetRect.height = 0;
    }
    if (cell.column < 0) {
      targetRect.x = visibleRect.x;
      targetRect.width = 0;
    }
    myMainTable.scrollRectToVisible(targetRect);
  }

  /**
   * The main table's cell that shows a grid cell, as the Swing positions {@code getCellRect} takes; while transposed its rows are the
   * grid's columns. An index is -1 on an axis the main table doesn't show.
   */
  private @NotNull TableCell tableCell(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    int viewRow = myMainTable.getRawIndexConverter().row2View().applyAsInt(row.value);
    int viewColumn = myMainTable.getRawIndexConverter().column2View().applyAsInt(column.value);
    return myMainTable.isTransposed() ? new TableCell(viewColumn, viewRow) : new TableCell(viewRow, viewColumn);
  }

  /** Whether the view shows {@code column}, in the main table or in the pinned one. */
  private boolean isShownInView(@NotNull ModelIndex<GridColumn> column) {
    return myGrid.getRawIndexConverter().column2View().applyAsInt(column.value) >= 0;
  }

  private void scrollSelectedRowsToVisible() {
    if (myRowSelection.isSelectionEmpty()) return;
    Rectangle first = myMainTable.getCellRect(myRowSelection.getMinSelectionIndex(), -1, false);
    Rectangle last = myMainTable.getCellRect(myRowSelection.getMaxSelectionIndex(), -1, false);
    int height = Math.min(last.y + last.height - first.y, myMainTable.getHeight());
    myMainTable.scrollRectToVisible(new Rectangle(myMainTable.getVisibleRect().x, first.y, 0, height));
  }
}
