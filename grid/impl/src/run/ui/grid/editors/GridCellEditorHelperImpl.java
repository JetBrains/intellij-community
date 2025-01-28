package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.datagrid.*;
import com.intellij.database.extractors.FormatterCreator;
import com.intellij.database.run.ReservedCellValue;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.GridEditGuard;
import com.intellij.database.run.ui.grid.editors.UnparsedValue.ParsingError;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Types;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import static com.intellij.database.run.ui.grid.editors.FormatsCache.*;

public class GridCellEditorHelperImpl implements GridCellEditorHelper {
  @Override
  public @NotNull UnparsedValue createUnparsedValue(@NotNull String text,
                                                    @Nullable ParsingError error,
                                                    @NotNull CoreGrid<GridRow, GridColumn> grid,
                                                    @NotNull ModelIndex<GridRow> row,
                                                    @NotNull ModelIndex<GridColumn> column) {
    return new UnparsedValue(text, error);
  }

  @Override
  public EnumSet<ReservedCellValue> getSpecialValues(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull ModelIndex<GridColumn> column) {
    return EnumSet.of(ReservedCellValue.NULL);
  }

  @Override
  public boolean isNullable(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull ModelIndex<GridColumn> idx) {
    return true;
  }

  @Override
  public @NotNull List<String> getEnumValues(@NotNull CoreGrid<GridRow, GridColumn> grid, ModelIndex<GridColumn> column) {
    return ContainerUtil.emptyList();
  }

  @Override
  public @NotNull BoundaryValueResolver getResolver(@NotNull CoreGrid<GridRow, GridColumn> grid, @Nullable ModelIndex<GridColumn> column) {
    return BoundaryValueResolver.ALWAYS_NULL;
  }

  @Override
  public boolean useBigDecimalWithPriorityType(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    return false;
  }

  @Override
  public boolean parseBigIntAsLong(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    return false;
  }

  @Override
  public boolean useLenientFormatterForTemporalObjects(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    return false;
  }

  @Override
  public @NotNull @Nls String getDateFormatSuffix(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull ModelIndex<GridColumn> column) {
    Formatter dateFormat = getDateFormat(grid, column);
    return dateFormat != null ? " (" + dateFormat + ") " : "";
  }

  protected @Nullable Formatter getDateFormat(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull ModelIndex<GridColumn> columnIdx) {
    int jdbcType = guessJdbcTypeForEditing(grid, null, columnIdx);
    FormatterCreator creator = FormatterCreator.get(grid);
    FormatsCache cache = FormatsCache.get(grid);
    GridColumn column = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(columnIdx);
    return jdbcType == Types.DATE ? cache.get(getDateFormatProvider(column, null), creator) :
           jdbcType == Types.TIME ? cache.get(getTimeFormatProvider(column, null), creator) :
           jdbcType == Types.TIMESTAMP ? cache.get(getTimestampFormatProvider(column, null), creator) : null;
  }

  @Override
  public int guessJdbcTypeForEditing(@NotNull CoreGrid<GridRow, GridColumn> grid,
                                     @Nullable ModelIndex<GridRow> row,
                                     @NotNull ModelIndex<GridColumn> column) {
    if (row != null && !row.isValid(grid) || !column.isValid(grid)) return Types.OTHER;
    GridModel<GridRow, GridColumn> model = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
    GridColumn c = model.getColumn(column);
    return c == null ? Types.OTHER : c.getType();
  }

  @Override
  public @Nullable ReservedCellValue getDefaultNullValue(@NotNull CoreGrid<GridRow, GridColumn> grid, @Nullable ModelIndex<GridColumn> column) {
    return ReservedCellValue.NULL;
  }

  @Override
  public boolean areValuesEqual(Object v1, Object v2, @Nullable CoreGrid<GridRow, GridColumn> grid) {
    return GridCellEditorHelper.areValuesEqual(v1, v2, numberEquals(null, grid));
  }

  protected BiFunction<Object, Object, ThreeState> numberEquals(@Nullable GridDataHookUp<GridRow, GridColumn> hookUp, @Nullable CoreGrid<GridRow, GridColumn> grid) {
    return GridCellEditorHelper::numberEquals;
  }

  @Override
  public @Nullable Set<GridEditGuard> getEditGuards() {
    return null;
  }
}
