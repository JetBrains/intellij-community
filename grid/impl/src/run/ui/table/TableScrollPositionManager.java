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

class TableScrollPositionManager implements GridScrollPositionManager {
  private final GridTable myResultView;
  private final DataGrid myGrid;

  TableScrollPositionManager(@NotNull GridTable resultView, @NotNull DataGrid grid) {
    myResultView = resultView;
    myGrid = grid;
  }

  public static void install(@NotNull GridTable resultView, @NotNull DataGrid grid) {
    TableScrollPositionManager tableScrollPositionManager = new TableScrollPositionManager(resultView, grid);
    resultView.putClientProperty(SCROLL_POSITION_MANAGER_KEY, tableScrollPositionManager);
  }

  @Override
  public GridScrollPosition store() {
    Rectangle visibleRect = myResultView.getVisibleRect();
    Point p = visibleRect.getLocation();
    p.x += 1;
    p.y += 1;
    // At the very edge remember the first row or column, even when it is too narrow for the point to land in it.
    int row = visibleRect.y == 0 ? 0 : myResultView.rowAtPoint(p);
    int column = visibleRect.x == 0 ? 0 : myResultView.columnAtPoint(p);
    int modelRow = myResultView.getRawIndexConverter().row2Model().applyAsInt(myResultView.isTransposed() ? column : row);
    int modelColumn = myResultView.getRawIndexConverter().column2Model().applyAsInt(myResultView.isTransposed() ? row : column);
    return new GridScrollPosition(ModelIndex.forRow(myGrid, modelRow), ModelIndex.forColumn(myGrid, modelColumn));
  }

  @Override
  public void restore(@NotNull GridScrollPosition position) {
    int viewRow = myResultView.getRawIndexConverter().row2View().applyAsInt(position.myTopRowIdx.value);
    int viewColumn = myResultView.getRawIndexConverter().column2View().applyAsInt(position.myLeftColumnIdx.value);
    Rectangle targetRect = myResultView.getCellRect(myResultView.isTransposed() ? viewColumn : viewRow,
                                                    myResultView.isTransposed() ? viewRow : viewColumn, true);
    Rectangle visibleRect = myResultView.getVisibleRect();
    // A column that another table of the view shows has no place here, so this table keeps its horizontal scroll.
    if (!myResultView.isTransposed() && viewColumn < 0 && isGridColumn(position.myLeftColumnIdx)) targetRect.x = visibleRect.x;
    targetRect.width = visibleRect.width;
    targetRect.height = visibleRect.height;
    myResultView.scrollRectToVisible(targetRect);
  }

  @Override
  public void scrollSelectionToVisible() {
    // A selection only in columns that another table of the view shows has no column here to scroll to, only rows.
    if (!myResultView.isTransposed() && myResultView.getSelectedColumnCount() == 0 &&
        myGrid.getSelectionModel().getSelectedColumnCount() > 0) {
      scrollSelectedRowsToVisible();
    }
    else {
      TableUtil.scrollSelectionToVisible(myResultView);
    }
  }

  /** Whether the grid shows {@code column}, in this table or in another table of the view. */
  private boolean isGridColumn(@NotNull ModelIndex<GridColumn> column) {
    return myGrid.getRawIndexConverter().column2View().applyAsInt(column.value) >= 0;
  }

  private void scrollSelectedRowsToVisible() {
    ListSelectionModel rows = myResultView.getSelectionModel();
    if (rows.isSelectionEmpty()) return;
    Rectangle first = myResultView.getCellRect(rows.getMinSelectionIndex(), -1, false);
    Rectangle last = myResultView.getCellRect(rows.getMaxSelectionIndex(), -1, false);
    int height = Math.min(last.y + last.height - first.y, myResultView.getHeight());
    myResultView.scrollRectToVisible(new Rectangle(myResultView.getVisibleRect().x, first.y, 0, height));
  }
}
