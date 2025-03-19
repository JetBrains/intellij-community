package com.intellij.database.run.ui.grid;

import com.intellij.database.datagrid.*;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public interface GridScrollPositionManager {
  String SCROLL_POSITION_MANAGER_KEY = "GridScrollPositionManager";

  void scrollSelectionToVisible();

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
    public GridScrollPosition store() {
      return new GridScrollPosition(ModelIndex.forRow(myGrid, -1), ModelIndex.forColumn(myGrid, -1));
    }

    @Override
    public void restore(@NotNull GridScrollPositionManager.GridScrollPosition position) {
    }
  }
}
