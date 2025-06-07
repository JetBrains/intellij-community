package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.*;
import com.intellij.database.editor.GotoRowAction;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.database.editor.GotoRowAction.GO_TO_ROW_EXECUTOR_KEY;

public class TableGoToRowHelper implements GotoRowAction.GoToRowHelper {
  private final TableResultView myTable;
  private final DataGrid myDataGrid;

  TableGoToRowHelper(@NotNull TableResultView table, @NotNull DataGrid grid) {
    myTable = table;
    myDataGrid = grid;
    table.putClientProperty(GO_TO_ROW_EXECUTOR_KEY, this);
  }

  @Override
  public void goToRow(@NotNull String rowStr, @NotNull String columnStr) {
    Couple<Integer> coordinates = coordinates(rowStr, columnStr);
    Counter counter = Counter.get(myTable);
    int column = counter.horizontalUnit(coordinates);
    int row = counter.verticalUnit(coordinates);
    ModelIndex<GridColumn> columnIdx = myTable.uiColumn(column);
    int absoluteRowIdx = myTable.fromRealRowIdx(row);
    if (columnIdx.isValid(myDataGrid) && absoluteRowIdx != -1) { // do not use ModelIndex#isValid for row as it can be not loaded yet
      myDataGrid.showCell(absoluteRowIdx, columnIdx);
    }
  }

  private @NotNull Couple<Integer> coordinates(String row, String column) {
    Counter counter = Counter.get(myTable);
    int rowIdx = counter.rowIndex(myDataGrid, row);
    int columnIdx = counter.columnIndex(myDataGrid, column);
    return Couple.of(rowIdx, columnIdx);
  }

  public enum Counter {
    REGULAR {
      @Override
      public int verticalUnit(@NotNull DataGrid grid) {
        return GridUtil.min(grid.getSelectionModel().getSelectedRows().toView(grid));
      }

      @Override
      public int horizontalUnit(@NotNull DataGrid grid) {
        return GridUtil.min(grid.getSelectionModel().getSelectedColumns().toView(grid));
      }

      @Override
      public int verticalUnit(@NotNull Couple<Integer> coordinates) {
        return coordinates.first;
      }

      @Override
      public int horizontalUnit(@NotNull Couple<Integer> coordinates) {
        return coordinates.second;
      }

      @Override
      public int rowIndex(@NotNull DataGrid grid, @NotNull String s) {
        return StringUtil.isEmpty(s) ? defaultRow(grid) : StringUtil.parseInt(s, -1);
      }

      @Override
      public int columnIndex(@NotNull DataGrid grid, @NotNull String s) {
        try {
          return Integer.parseInt(s);
        }
        catch (NumberFormatException ignore) {
        }
        if (StringUtil.isEmpty(s)) return defaultColumn(grid);

        List<ModelIndex<GridColumn>> indexList = grid.getVisibleColumns().asList();
        GridModel<GridRow, GridColumn> model = grid.getDataModel(DataAccessType.DATABASE_DATA);
        ModelIndex<GridColumn> idx = ContainerUtil.find(indexList, c -> {
          GridColumn column = model.getColumn(c);
          return column != null && StringUtil.equalsIgnoreCase(column.getName(), s);
        });
        return idx == null ? defaultColumn(grid) : idx.toView(grid).asInteger() + 1;
      }
    },
    TRANSPOSED {
      @Override
      public int verticalUnit(@NotNull DataGrid grid) {
        return REGULAR.horizontalUnit(grid);
      }

      @Override
      public int horizontalUnit(@NotNull DataGrid grid) {
        return REGULAR.verticalUnit(grid);
      }

      @Override
      public int verticalUnit(@NotNull Couple<Integer> coordinates) {
        return REGULAR.horizontalUnit(coordinates);
      }

      @Override
      public int horizontalUnit(@NotNull Couple<Integer> coordinates) {
        return REGULAR.verticalUnit(coordinates);
      }

      @Override
      public int rowIndex(@NotNull DataGrid grid, @NotNull String s) {
        return REGULAR.columnIndex(grid, s);
      }

      @Override
      public int columnIndex(@NotNull DataGrid grid, @NotNull String s) {
        return REGULAR.rowIndex(grid, s);
      }
    };

    public abstract int horizontalUnit(@NotNull DataGrid grid);
    public abstract int horizontalUnit(@NotNull Couple<Integer> coordinates);
    public abstract int verticalUnit(@NotNull DataGrid grid);
    public abstract int verticalUnit(@NotNull Couple<Integer> coordinates);
    public abstract int rowIndex(@NotNull DataGrid grid, @NotNull String s);
    public abstract int columnIndex(@NotNull DataGrid grid, @NotNull String s);

    private static int defaultColumn(@NotNull DataGrid dataGrid) {
      int min = GridUtil.min(dataGrid.getSelectionModel().getSelectedColumns().toView(dataGrid));
      int result = min == -1 ? GridUtil.min(dataGrid.getVisibleColumns().toView(dataGrid)) : min;
      return result == -1 ? -1 : result + 1;
    }

    private static int defaultRow(@NotNull DataGrid dataGrid) {
      int min = GridUtil.min(dataGrid.getSelectionModel().getSelectedRows().toView(dataGrid));
      int result = min == -1 ? GridUtil.min(dataGrid.getVisibleRows().toView(dataGrid)) : min;
      return result == -1 ? -1 : result + 1;
    }

    public static @NotNull Counter get(@NotNull TableResultView table) {
      return table.isTransposed() ? TRANSPOSED : REGULAR;
    }
  }
}
