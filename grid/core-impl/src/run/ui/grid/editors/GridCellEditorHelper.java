package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.datagrid.CoreGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.remote.jdbc.LobInfo;
import com.intellij.database.run.ReservedCellValue;
import com.intellij.database.run.ui.GridEditGuard;
import com.intellij.database.run.ui.grid.editors.UnparsedValue.ParsingError;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;

public interface GridCellEditorHelper {
  Key<GridCellEditorHelper> GRID_CELL_EDITOR_HELPER_KEY = new Key<>("GRID_CELL_EDITOR_HELPER_KEY");

  @NotNull
  UnparsedValue createUnparsedValue(@NotNull String text,
                                    @Nullable ParsingError error,
                                    @NotNull CoreGrid<GridRow, GridColumn> grid,
                                    @NotNull ModelIndex<GridRow> row,
                                    @NotNull ModelIndex<GridColumn> column);

  EnumSet<ReservedCellValue> getSpecialValues(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull ModelIndex<GridColumn> column);

  boolean isNullable(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull ModelIndex<GridColumn> idx);

  @NotNull
  List<String> getEnumValues(@NotNull CoreGrid<GridRow, GridColumn> grid, ModelIndex<GridColumn> column);

  @NotNull
  BoundaryValueResolver getResolver(@NotNull CoreGrid<GridRow, GridColumn> grid, @Nullable ModelIndex<GridColumn> column);

  boolean useBigDecimalWithPriorityType(@NotNull CoreGrid<GridRow, GridColumn> grid);

  boolean parseBigIntAsLong(@NotNull CoreGrid<GridRow, GridColumn> grid);

  boolean useLenientFormatterForTemporalObjects(@NotNull CoreGrid<GridRow, GridColumn> grid);

  @Nls
  @NotNull
  String getDateFormatSuffix(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull ModelIndex<GridColumn> column);

  static boolean areValuesEqual(Object v1, Object v2) {
    return areValuesEqual(v1, v2, GridCellEditorHelper::numberEquals);
  }

  static boolean areValuesEqual(Object v1, Object v2, @NotNull BiFunction<Object, Object, ThreeState> numberEquals) {
    if (v1 == ReservedCellValue.NULL) v1 = null;
    if (v2 == ReservedCellValue.NULL) v2 = null;

    ThreeState result = simpleEquals(v1, v2);
    if (result == ThreeState.UNSURE) result = numberEquals.apply(v1, v2);
    if (result == ThreeState.UNSURE) result = arrayEquals(v1, v2);
    if (result == ThreeState.UNSURE) result = clobEquals(v1, v2);
    if (result == ThreeState.UNSURE) result = ThreeState.NO;

    return result.toBoolean();
  }

  static ThreeState simpleEquals(Object v1, Object v2) {
    return Comparing.equal(v1, v2) ? ThreeState.YES : ThreeState.UNSURE;
  }

  static ThreeState arrayEquals(Object v1, Object v2) {
    if (v1 != null && v2 != null && v1.getClass() == v2.getClass() && v1.getClass().isArray()) {
      if (v1 instanceof byte[]) {
        return ThreeState.fromBoolean(Arrays.equals((byte[])v1, (byte[])v2));
      }
      else if (v1 instanceof char[]) {
        return ThreeState.fromBoolean(Arrays.equals((char[])v1, (char[])v2));
      }
    }
    return ThreeState.UNSURE;
  }

  static ThreeState clobEquals(Object v1, Object v2) {
    LobInfo.ClobInfo info1 = ObjectUtils.tryCast(v1, LobInfo.ClobInfo.class);
    LobInfo.ClobInfo info2 = ObjectUtils.tryCast(v2, LobInfo.ClobInfo.class);
    if(info1 != null && info2 != null) {
      return ThreeState.fromBoolean(!info2.isTruncated() && clobStringEquals(info1, info2.data));
    }
    else if (info1 != null && v2 instanceof String) {
      return ThreeState.fromBoolean(clobStringEquals(info1, (String)v2));
    }
    else if (info2 != null && v1 instanceof String) {
      return ThreeState.fromBoolean(clobStringEquals(info2, (String)v1));
    }
    return ThreeState.UNSURE;
  }

  static boolean clobStringEquals(@NotNull LobInfo.ClobInfo lob, @Nullable String s) {
    return !lob.isTruncated() && Objects.equals(lob.data, s);
  }

  static ThreeState numberEquals(Object v1, Object v2) {
    Number n1 = ObjectUtils.tryCast(v1, Number.class);
    Number n2 = ObjectUtils.tryCast(v2, Number.class);
    if (n1 != null && n2 != null) {
      //double is too rough for large integers
      long l1 = n1.longValue();
      long l2 = n2.longValue();
      double d1 = n1.doubleValue();
      double d2 = n2.doubleValue();
      return ThreeState.fromBoolean(l1 == l2 && (d1 == d2 || Double.isNaN(d1) && Double.isNaN(d2)));
    }
    return ThreeState.UNSURE;
  }

  @Nullable
  Set<GridEditGuard> getEditGuards();

  static @NotNull GridCellEditorHelper get(@NotNull CoreGrid<?, ?> grid) {
    return Objects.requireNonNull(GRID_CELL_EDITOR_HELPER_KEY.get(grid));
  }

  static void set(@NotNull CoreGrid<?, ?> grid, @NotNull GridCellEditorHelper helper) {
    GRID_CELL_EDITOR_HELPER_KEY.set(grid, helper);
  }

  int guessJdbcTypeForEditing(@NotNull CoreGrid<GridRow, GridColumn> grid, @Nullable ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column);

  boolean areValuesEqual(Object v1, Object v2, @Nullable CoreGrid<GridRow, GridColumn> grid);

  @Nullable
  ReservedCellValue getDefaultNullValue(@NotNull CoreGrid<GridRow, GridColumn> grid, @Nullable ModelIndex<GridColumn> column);
}
