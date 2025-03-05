package com.intellij.database.datagrid.color;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface ColorLayer extends Comparable<ColorLayer> {

  @Nullable
  Color getCellBackground(@NotNull ModelIndex<GridRow> row,
                          @NotNull ModelIndex<GridColumn> column,
                          @NotNull DataGrid grid,
                          @Nullable Color color);

  @Nullable
  Color getRowHeaderBackground(@NotNull ModelIndex<GridRow> row,
                               @NotNull DataGrid grid,
                               @Nullable Color color);

  @Nullable
  Color getColumnHeaderBackground(@NotNull ModelIndex<GridColumn> column,
                                  @NotNull DataGrid grid,
                                  @Nullable Color color);

  default @Nullable Color getColumnHeaderBackground(@NotNull ModelIndex<GridColumn> column,
                                                    int headerLine,
                                                    @NotNull DataGrid grid,
                                                    @Nullable Color color) {
    return getColumnHeaderBackground(column, grid, color);
  }

  default @Nullable Color getRowHeaderForeground(@NotNull ModelIndex<GridRow> row,
                                                 @NotNull DataGrid grid,
                                                 @Nullable Color color) {
    return color;
  }

  default @Nullable Color getColumnHeaderForeground(@NotNull ModelIndex<GridColumn> column,
                                                    @NotNull DataGrid grid,
                                                    @Nullable Color color) {
    return color;
  }

  int getPriority();

  @Override
  default int compareTo(@NotNull ColorLayer o) {
    return Integer.compare(getPriority(), o.getPriority());
  }
}
