package com.intellij.database.datagrid.color;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.run.ui.grid.GridSearchSession;
import com.intellij.database.util.DataGridUIUtil;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class SearchSessionColorLayer implements ColorLayer {
  @Override
  public @Nullable Color getCellBackground(@NotNull ModelIndex<GridRow> row,
                                           @NotNull ModelIndex<GridColumn> column,
                                           @NotNull DataGrid grid,
                                           @Nullable Color color) {
    //noinspection unchecked
    GridSearchSession<GridRow, GridColumn> searchSession =
      ObjectUtils.tryCast(grid.getSearchSession(), GridSearchSession.class);
    if (searchSession != null && searchSession.isMatchedCell(row, column)) {
      TextAttributes searchMatchAttributes = grid.getColorsScheme().getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES);
      Color searchMatchBackground = searchMatchAttributes.getBackgroundColor();
      return searchMatchBackground != null ? searchMatchBackground : DataGridUIUtil.softHighlightOf(color);
    }
    return color;
  }

  @Override
  public @Nullable Color getRowHeaderBackground(@NotNull ModelIndex<GridRow> row, @NotNull DataGrid grid, @Nullable Color color) {
    return color;
  }

  @Override
  public @Nullable Color getColumnHeaderBackground(@NotNull ModelIndex<GridColumn> column, @NotNull DataGrid grid, @Nullable Color color) {
    return color;
  }

  @Override
  public int getPriority() {
    return 4;
  }
}
