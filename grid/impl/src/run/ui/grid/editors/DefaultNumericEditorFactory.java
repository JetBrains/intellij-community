package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.datagrid.CoreGrid;
import com.intellij.database.datagrid.GridCellRequest;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.extractors.FormatterCreator;
import com.intellij.database.extractors.ObjectFormatterUtil;
import com.intellij.database.run.ReservedCellValue;
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
  protected @NotNull Formatter getFormatInner(@NotNull GridCellRequest<GridRow, GridColumn> request) {
    Formatter format = getFormat(request);
    if (format != null) {
      return format;
    }
    GridColumn c = Objects.requireNonNull(request.getColumn());
    return FormatterCreator.get(request.getGrid()).create(getDecimalKey(c, null));
  }

  @Override
  public int getSuitability(@NotNull GridCellRequest<GridRow, GridColumn> request) {
    return ObjectFormatterUtil.isNumericCell(request) && getFormat(request) != null ? SUITABILITY_MIN : SUITABILITY_UNSUITABLE;
  }

  private static @Nullable Formatter getFormat(@NotNull GridCellRequest<GridRow, GridColumn> request) {
    GridColumn c = Objects.requireNonNull(request.getColumn());
    CoreGrid<GridRow, GridColumn> grid = request.getGrid();
    FormatsCache formatsCache = FormatsCache.get(grid);
    FormatterCreator creator = FormatterCreator.get(grid);

    GridCellEditorHelper helper = GridCellEditorHelper.get(grid);
    int type = helper.guessJdbcTypeForEditing(request);
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
                                                                @NotNull GridCellRequest<GridRow, GridColumn> request,
                                                                @NotNull Formatter format,
                                                                @Nullable ReservedCellValue nullValue,
                                                                EventObject initiator,
                                                                @Nullable TextCompletionProvider provider,
                                                                @NotNull ValueParser valueParser,
                                                                @NotNull ValueFormatter valueFormatter) {
    return new NumericEditor(project, request, format, nullValue, initiator, provider, valueParser, valueFormatter);
  }

  private static class NumericEditor extends FormatBasedGridCellEditor {

    NumericEditor(@NotNull Project project,
                  @NotNull GridCellRequest<GridRow, GridColumn> request,
                  @NotNull Formatter format,
                  @Nullable ReservedCellValue nullValue,
                  @Nullable EventObject initiator,
                  @Nullable TextCompletionProvider provider,
                  @NotNull ValueParser valueParser,
                  @NotNull ValueFormatter valueFormatter) {
      super(project, request, format, nullValue, initiator, provider, valueParser, valueFormatter, false);
      getTextField().addSettingsProvider(editor -> {
        GridUtil.registerArrowAction(editor, getGrid(), this::getEditor);
        GridUtil.configureNumericEditor(getGrid(), editor);
      });
    }
  }
}
