package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.datagrid.GridCellRequest;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.extractors.FormatterCreator;
import org.jetbrains.annotations.NotNull;

import java.sql.Time;
import java.sql.Types;
import java.util.Date;

public class DefaultTimeEditorFactory extends DefaultTemporalEditorFactory {
  @Override
  protected @NotNull Formatter getFormatInner(@NotNull GridCellRequest<GridRow, GridColumn> request) {
    FormatsCache cache = FormatsCache.get(request.getGrid());
    return cache.get(FormatsCache.getTimeFormatProvider(null, null), FormatterCreator.get(request.getGrid()));
  }

  @Override
  public int getSuitability(@NotNull GridCellRequest<GridRow, GridColumn> request) {
    return GridCellEditorHelper.get(request.getGrid()).guessJdbcTypeForEditing(request) == Types.TIME ? SUITABILITY_MIN : SUITABILITY_UNSUITABLE;
  }

  @Override
  public @NotNull ValueParser getValueParser(@NotNull GridCellRequest<GridRow, GridColumn> request) {
    ValueParser parser = super.getValueParser(request);
    return (text, document) -> {
      Object v = parser.parse(text, document);
      return v instanceof Date ? new Time(((Date)v).getTime()) : v;
    };
  }
}
