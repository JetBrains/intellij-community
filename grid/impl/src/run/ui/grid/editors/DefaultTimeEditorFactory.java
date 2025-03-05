package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.extractors.FormatterCreator;
import org.jetbrains.annotations.NotNull;

import java.sql.Time;
import java.sql.Types;
import java.util.Date;

public class DefaultTimeEditorFactory extends DefaultTemporalEditorFactory {
  @Override
  protected @NotNull Formatter getFormatInner(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    FormatsCache cache = FormatsCache.get(grid);
    return cache.get(FormatsCache.getTimeFormatProvider(null, null), FormatterCreator.get(grid));
  }

  @Override
  public int getSuitability(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    return GridCellEditorHelper.get(grid).guessJdbcTypeForEditing(grid, row, column) == Types.TIME ? SUITABILITY_MIN : SUITABILITY_UNSUITABLE;
  }

  @Override
  public @NotNull ValueParser getValueParser(@NotNull DataGrid grid,
                                             @NotNull ModelIndex<GridRow> rowIdx,
                                             @NotNull ModelIndex<GridColumn> columnIdx) {
    ValueParser parser = super.getValueParser(grid, rowIdx, columnIdx);
    return (text, document) -> {
      Object v = parser.parse(text, document);
      return v instanceof Date ? new Time(((Date)v).getTime()) : v;
    };
  }
}
