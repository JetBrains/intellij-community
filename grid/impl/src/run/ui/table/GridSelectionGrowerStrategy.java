package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.*;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

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
    public void changeSelection(@NotNull DataGrid panel, @NotNull ResultView resultView, @NotNull JBTable table) {
      JScrollPane pane = (JScrollPane)panel.getMainResultViewComponent();
      Point position = pane.getViewport() == null ? new Point(0, 0) : pane.getViewport().getViewPosition();
      int rowAtPoint = get(table.rowAtPoint(position), table.getRowCount());
      int columnAtPoint = get(table.columnAtPoint(position), table.getColumnCount());
      boolean transposed = resultView.isTransposed();
      panel.getSelectionModel().setSelection(ViewIndex.forRow(panel, !transposed ? rowAtPoint : columnAtPoint).toModel(panel),
                                             ViewIndex.forColumn(panel, !transposed ? columnAtPoint : rowAtPoint).toModel(panel));
    }

    private static int get(int value, int upperBound) {
      return value < 0 || value >= upperBound ? 0 : value;
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
    public void changeSelection(@NotNull DataGrid panel, @NotNull ResultView resultView, @NotNull JBTable table) {
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
    public void changeSelection(@NotNull DataGrid panel, @NotNull ResultView resultView, @NotNull JBTable table) {
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
    public void changeSelection(@NotNull DataGrid panel, @NotNull ResultView resultView, @NotNull JBTable table) {
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

  abstract void changeSelection(@NotNull DataGrid panel, @NotNull ResultView resultView, @NotNull JBTable table);

  abstract boolean isSuitable(@NotNull ViewIndexSet<GridRow> selectedRows,
                              @NotNull ViewIndexSet<GridColumn> selectedColumns,
                              int rowCount,
                              int columnCount,
                              boolean transposed);
  static @Nullable GridSelectionGrowerStrategy of(@NotNull DataGrid grid, @NotNull ResultView view) {
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
