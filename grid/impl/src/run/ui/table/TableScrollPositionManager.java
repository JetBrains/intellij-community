package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.run.ui.grid.GridScrollPositionManager;
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
    int viewRow = myMainTable.getRawIndexConverter().row2View().applyAsInt(position.myTopRowIdx.value);
    int viewColumn = myMainTable.getRawIndexConverter().column2View().applyAsInt(position.myLeftColumnIdx.value);
    Rectangle targetRect = myMainTable.getCellRect(myMainTable.isTransposed() ? viewColumn : viewRow,
                                                   myMainTable.isTransposed() ? viewRow : viewColumn, true);
    Rectangle visibleRect = myMainTable.getVisibleRect();
    // A column that the pinned table shows has no place in the main table, so the main table keeps its horizontal scroll.
    if (!myMainTable.isTransposed() && viewColumn < 0 && isShownInView(position.myLeftColumnIdx)) targetRect.x = visibleRect.x;
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

  /** The main table's first selected cell; with no selection {@code getCellRect(-1, -1)} is the top left corner. */
  @Override
  public void scrollFirstSelectedCellToVisible() {
    scrollTableCellToVisible(myMainTable.getSelectedRow(), myMainTable.getSelectedColumn());
  }

  /** Takes the main table's indices; a row or a column past the end is the table's far edge. */
  @Override
  public void scrollTableCellToVisible(int row, int column) {
    myMainTable.scrollRectToVisible(myMainTable.getCellRect(row, column, true));
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
