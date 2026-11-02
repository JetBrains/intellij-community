package com.intellij.database.run.ui.grid;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ResultView;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

/** Stores, restores and changes the grid's scroll position. */
public interface GridScrollPositionManager {
  String SCROLL_POSITION_MANAGER_KEY = "GridScrollPositionManager";

  void scrollSelectionToVisible();

  /** Scrolls the scrolling part as little as needed to show its first selected cell, or its top left cell with no selection. */
  void scrollFirstSelectedCellToVisible();

  /**
   * Scrolls the scrolling part as little as needed to show one of its cells. {@code row} and {@code column} are its own indices, as its
   * {@code rowAtPoint} and {@code columnAtPoint} give them, not grid indices.
   */
  void scrollTableCellToVisible(int row, int column);

  static @NotNull GridScrollPositionManager get(@NotNull ResultView resultView, @NotNull DataGrid grid) {
    GridScrollPositionManager manager =
      ObjectUtils.tryCast(resultView.getComponent().getClientProperty(SCROLL_POSITION_MANAGER_KEY), GridScrollPositionManager.class);
    return manager != null ? manager : new DummyScrollPositionManager(grid);
  }

  GridScrollPosition store();

  void restore(@NotNull GridScrollPositionManager.GridScrollPosition position);

  class GridScrollPosition {
    public final ModelIndex<GridRow> myTopRowIdx;
    public final ModelIndex<GridColumn> myLeftColumnIdx;

    public GridScrollPosition(@NotNull ModelIndex<GridRow> topRowIdx, @NotNull ModelIndex<GridColumn> leftColumnIdx) {
      myTopRowIdx = topRowIdx;
      myLeftColumnIdx = leftColumnIdx;
    }
  }

  class DummyScrollPositionManager implements GridScrollPositionManager {
    private final DataGrid myGrid;

    DummyScrollPositionManager(@NotNull DataGrid grid) {
      myGrid = grid;
    }

    @Override
    public void scrollSelectionToVisible() {
    }

    @Override
    public void scrollFirstSelectedCellToVisible() {
    }

    @Override
    public void scrollTableCellToVisible(int row, int column) {
    }

    @Override
    public GridScrollPosition store() {
      return new GridScrollPosition(ModelIndex.forRow(myGrid, -1), ModelIndex.forColumn(myGrid, -1));
    }

    @Override
    public void restore(@NotNull GridScrollPositionManager.GridScrollPosition position) {
    }
  }
}
