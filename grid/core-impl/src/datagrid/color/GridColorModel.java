package com.intellij.database.datagrid.color;

import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface GridColorModel {
  @Nullable
  Color getCellBackground(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column);

  @Nullable
  Color getRowHeaderBackground(@NotNull ModelIndex<GridRow> row);

  @Nullable
  Color getColumnHeaderBackground(@NotNull ModelIndex<GridColumn> column);

  default @Nullable Color getColumnHeaderBackground(@NotNull ModelIndex<GridColumn> column, int headerLine) {
    return getColumnHeaderBackground(column);
  }

  @NotNull
  Color getRowHeaderForeground(@NotNull ModelIndex<GridRow> row);

  @NotNull
  Color getColumnHeaderForeground(@NotNull ModelIndex<GridColumn> column);
}
