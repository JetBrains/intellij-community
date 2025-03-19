package com.intellij.database.data.types;

import com.intellij.database.datagrid.CoreGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.mutating.CellMutation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DataTypeConversion {
  @NotNull
  CellMutation.Builder convert(@NotNull ConversionGraph graph);

  @NotNull
  CellMutation.Builder build();

  boolean isValid();

  interface Builder {
    @NotNull Builder copy();
    @NotNull Builder value(@Nullable Object value);
    @NotNull Builder firstColumn(@NotNull GridColumn column);
    int firstRowIdx();
    @NotNull Builder firstRowIdx(int rowIdx);
    @NotNull Builder firstColumnIdx(int columnIdx);
    int firstColumnIdx();
    @NotNull Builder firstGrid(@Nullable CoreGrid<GridRow, GridColumn> grid);
    @NotNull Builder secondGrid(@NotNull CoreGrid<GridRow, GridColumn> grid);
    @NotNull Builder offset(int rows, int columns);
    int size();
    @NotNull DataTypeConversion build();
  }
}
