package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ViewIndex;
import com.intellij.database.datagrid.ViewIndexSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


enum GridSelectionGrowerStrategy {
  CELL {
    @Override
    boolean isSuitable(@NotNull ViewIndexSet<GridRow> selectedRows,
                       @NotNull ViewIndexSet<GridColumn> selectedColumns,
                       int rowCount,
                       int columnCount,
                       boolean transposed) {
      return selectedRows.size() == 0 || selectedColumns.size() == 0;
    }

    @Override
    public void changeSelection(@NotNull DataGrid panel, @NotNull TableResultView view) {
      var cell = view.firstVisibleCell();
      panel.getSelectionModel().setSelection(cell.getFirst().toModel(panel), cell.getSecond().toModel(panel));
    }
  },
  COLUMN(true) {
    @Override
    boolean isSuitable(@NotNull ViewIndexSet<GridRow> selectedRows,
                       @NotNull ViewIndexSet<GridColumn> selectedColumns,
                       int rowCount,
                       int columnCount,
                       boolean transposed) {
      return notAllSelected(selectedRows, selectedColumns, rowCount, columnCount) &&
             (!transposed && selectedRows.size() < rowCount || transposed && selectedColumns.size() < columnCount);
    }

    @Override
    public void changeSelection(@NotNull DataGrid panel, @NotNull TableResultView view) {
      panel.getSelectionModel().selectWholeColumn();
    }
  },
  LINE(true, false) {
    @Override
    boolean isSuitable(@NotNull ViewIndexSet<GridRow> selectedRows,
                       @NotNull ViewIndexSet<GridColumn> selectedColumns,
                       int rowCount,
                       int columnCount,
                       boolean transposed) {
      return notAllSelected(selectedRows, selectedColumns, rowCount, columnCount) &&
             (!transposed && selectedRows.size() == rowCount || transposed && selectedColumns.size() == columnCount);
    }

    @Override
    public void changeSelection(@NotNull DataGrid panel, @NotNull TableResultView view) {
      panel.getSelectionModel().selectWholeRow();
    }
  },
  ALL {
    @Override
    boolean isSuitable(@NotNull ViewIndexSet<GridRow> selectedRows,
                       @NotNull ViewIndexSet<GridColumn> selectedColumns,
                       int rowCount,
                       int columnCount,
                       boolean transposed) {
      return notAllSelected(selectedRows, selectedColumns, rowCount, columnCount) &&
             (transposed && selectedColumns.size() == columnCount || !transposed && selectedRows.size() == rowCount);
    }

    @Override
    public void changeSelection(@NotNull DataGrid panel, @NotNull TableResultView view) {
      panel.getSelectionModel().selectWholeRow();
      panel.getSelectionModel().selectWholeColumn();
    }
  };

  private final boolean myNeedRestore;
  private final boolean myNeedStore;

  GridSelectionGrowerStrategy() {
    this(false, false);
  }

  GridSelectionGrowerStrategy(boolean needStore) {
    this(false, needStore);
  }

  GridSelectionGrowerStrategy(boolean needRestore, boolean needStore) {
    myNeedRestore = needRestore;
    myNeedStore = needStore;
  }

  boolean isNeedRestore() {
    return myNeedRestore;
  }

  boolean isNeedStore() {
    return myNeedStore;
  }

  abstract void changeSelection(@NotNull DataGrid panel, @NotNull TableResultView view);

  abstract boolean isSuitable(@NotNull ViewIndexSet<GridRow> selectedRows,
                              @NotNull ViewIndexSet<GridColumn> selectedColumns,
                              int rowCount,
                              int columnCount,
                              boolean transposed);
  static @Nullable GridSelectionGrowerStrategy of(@NotNull DataGrid grid, @NotNull TableResultView view) {
    ViewIndexSet<GridRow> rows = grid.getSelectionModel().getSelectedRows().toView(grid);
    ViewIndexSet<GridColumn> columns = grid.getSelectionModel().getSelectedColumns().toView(grid);
    int rowCount = grid.getVisibleRows().size();
    int columnCount = grid.getVisibleColumns().size();
    boolean transposed = view.isTransposed();
    for (GridSelectionGrowerStrategy grower : values()) {
      if (grower.isSuitable(rows, columns, rowCount, columnCount, transposed)) return grower;
    }
    return null;
  }

  private static boolean notAllSelected(@NotNull ViewIndexSet<GridRow> rows,
                                        @NotNull ViewIndexSet<GridColumn> columns,
                                        int rowCount,
                                        int columnCount) {
    return rows.size() * columns.size() < rowCount * columnCount;
  }
}
