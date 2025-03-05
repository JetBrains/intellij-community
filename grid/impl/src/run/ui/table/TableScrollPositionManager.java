package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.run.ui.grid.GridScrollPositionManager;
import com.intellij.ui.TableUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class TableScrollPositionManager implements GridScrollPositionManager {
  private final TableResultView myResultView;
  private final DataGrid myGrid;

  TableScrollPositionManager(@NotNull TableResultView resultView, @NotNull DataGrid grid) {
    myResultView = resultView;
    myGrid = grid;
    myResultView.putClientProperty(SCROLL_POSITION_MANAGER_KEY, this);
  }

  @Override
  public GridScrollPosition store() {
    Rectangle visibleRect = myResultView.getVisibleRect();
    Point p = visibleRect.getLocation();
    p.x += 1;
    p.y += 1;
    int row = myResultView.rowAtPoint(p);
    int column = myResultView.columnAtPoint(p);
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
    targetRect.width = visibleRect.width;
    targetRect.height = visibleRect.height;
    myResultView.scrollRectToVisible(targetRect);
  }

  @Override
  public void scrollSelectionToVisible() {
    TableUtil.scrollSelectionToVisible(myResultView);
  }
}
