package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.extractors.FormatterCreator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Time;
import java.sql.Types;
import java.util.Date;

public class DefaultTimeEditorFactory extends DefaultTemporalEditorFactory {
  @Override
  protected @NotNull Formatter getFormatInner(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column, @Nullable Object value) {
    FormatsCache cache = FormatsCache.get(grid);
    return cache.get(FormatsCache.getTimeFormatProvider(null, null), FormatterCreator.get(grid));
  }

  @Override
  public int getSuitability(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column,
                            @Nullable Object value) {
    return GridCellEditorHelper.get(grid).guessJdbcTypeForEditing(grid, row, column, value) == Types.TIME ? SUITABILITY_MIN : SUITABILITY_UNSUITABLE;
  }

  @Override
  public @NotNull ValueParser getValueParser(@NotNull DataGrid grid,
                                             @NotNull ModelIndex<GridRow> rowIdx,
                                             @NotNull ModelIndex<GridColumn> columnIdx,
                                             @Nullable Object value) {
    ValueParser parser = super.getValueParser(grid, rowIdx, columnIdx, value);
    return (text, document) -> {
      Object v = parser.parse(text, document);
      return v instanceof Date ? new Time(((Date)v).getTime()) : v;
    };
  }
}
