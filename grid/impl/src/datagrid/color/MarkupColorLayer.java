package com.intellij.database.datagrid.color;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.run.ui.grid.CellAttributes;
import com.intellij.database.run.ui.grid.GridMarkupModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class MarkupColorLayer implements ColorLayer {
  @Override
  public @Nullable Color getCellBackground(@NotNull ModelIndex<GridRow> row,
                                           @NotNull ModelIndex<GridColumn> column,
                                           @NotNull DataGrid grid,
                                           @Nullable Color color) {
    GridMarkupModel<GridRow, GridColumn> model = grid.getMarkupModel();
    CellAttributes attributes = model.getCellAttributes(row, column, grid.getColorsScheme());
    Color bg = attributes == null ? null : attributes.getBackgroundColor();
    return bg == null ? color : bg;
  }

  @Override
  public @Nullable Color getRowHeaderBackground(@NotNull ModelIndex<GridRow> row, @NotNull DataGrid grid, @Nullable Color color) {
    GridMarkupModel<GridRow, GridColumn> model = grid.getMarkupModel();
    CellAttributes attributes = model.getRowHeaderAttributes(row, grid.getColorsScheme());
    Color bg = attributes == null ? null : attributes.getBackgroundColor();
    return bg == null ? color : bg;
  }

  @Override
  public @Nullable Color getColumnHeaderBackground(@NotNull ModelIndex<GridColumn> column, @NotNull DataGrid grid, @Nullable Color color) {
    GridMarkupModel<GridRow, GridColumn> model = grid.getMarkupModel();
    CellAttributes attributes = model.getColumnHeaderAttributes(column, grid.getColorsScheme());
    Color bg = attributes == null ? null : attributes.getBackgroundColor();
    return bg == null ? color : bg;
  }

  @Override
  public int getPriority() {
    return 2;
  }
}
