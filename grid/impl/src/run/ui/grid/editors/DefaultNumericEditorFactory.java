package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.datagrid.*;
import com.intellij.database.extractors.FormatterCreator;
import com.intellij.database.extractors.ObjectFormatterUtil;
import com.intellij.database.run.ReservedCellValue;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.openapi.project.Project;
import com.intellij.util.textCompletion.TextCompletionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Types;
import java.util.EventObject;
import java.util.Objects;

import static com.intellij.database.extractors.FormatterCreator.getDecimalKey;

public class DefaultNumericEditorFactory extends FormatBasedGridCellEditorFactory {
  @Override
  protected @NotNull Formatter getFormatInner(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    Formatter format = getFormat(grid, row, column);
    if (format != null) {
      return format;
    }
    GridColumn c = Objects.requireNonNull(grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(column));
    return FormatterCreator.get(grid).create(getDecimalKey(c, null));
  }

  @Override
  public int getSuitability(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    return ObjectFormatterUtil.isNumericCell(grid, row, column) && getFormat(grid, row, column) != null ? SUITABILITY_MIN : SUITABILITY_UNSUITABLE;
  }

  private static @Nullable Formatter getFormat(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    GridModel<GridRow, GridColumn> model = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
    GridColumn c = Objects.requireNonNull(model.getColumn(column));
    FormatsCache formatsCache = FormatsCache.get(grid);
    FormatterCreator creator = FormatterCreator.get(grid);

    GridCellEditorHelper helper = GridCellEditorHelper.get(grid);
    int type = helper.guessJdbcTypeForEditing(grid, row, column);
    if (helper.useBigDecimalWithPriorityType(grid)) return formatsCache.get(FormatsCache.getBigDecimalWithPriorityTypeFormatProvider(type, null), creator);
    return switch (type) {
      case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> formatsCache.get(FormatsCache.getLongFormatProvider(null), creator);
      case Types.BIGINT -> helper.parseBigIntAsLong(grid)
                           ? formatsCache.get(FormatsCache.getLongFormatProvider(null), creator)
                           : formatsCache.get(FormatsCache.getBigIntFormatProvider(null), creator);
      case Types.REAL, Types.FLOAT, Types.DOUBLE, Types.DECIMAL, Types.NUMERIC -> creator.create(getDecimalKey(c, null));
      default -> null;
    };
  }

  @Override
  protected @NotNull FormatBasedGridCellEditor createEditorImpl(@NotNull Project project,
                                                                @NotNull DataGrid grid,
                                                                @NotNull Formatter format,
                                                                @Nullable ReservedCellValue nullValue,
                                                                EventObject initiator,
                                                                @Nullable TextCompletionProvider provider,
                                                                @NotNull ModelIndex<GridRow> row,
                                                                @NotNull ModelIndex<GridColumn> column,
                                                                @NotNull ValueParser valueParser,
                                                                @NotNull ValueFormatter valueFormatter) {
    return new NumericEditor(project, grid, format, row, column, nullValue, initiator, provider, valueParser, valueFormatter);
  }

  private static class NumericEditor extends FormatBasedGridCellEditor {

    NumericEditor(@NotNull Project project,
                  @NotNull DataGrid grid,
                  @NotNull Formatter format,
                  @NotNull ModelIndex<GridRow> row,
                  @NotNull ModelIndex<GridColumn> column,
                  @Nullable ReservedCellValue nullValue,
                  @Nullable EventObject initiator,
                  @Nullable TextCompletionProvider provider,
                  @NotNull ValueParser valueParser,
                  @NotNull ValueFormatter valueFormatter) {
      super(project, grid, format, column, row, nullValue, initiator, provider, valueParser, valueFormatter, false);
      getTextField().addSettingsProvider(editor -> GridUtil.configureNumericEditor(grid, editor));
    }
  }
}
