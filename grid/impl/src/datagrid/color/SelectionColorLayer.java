package com.intellij.database.datagrid.color;

import com.intellij.database.datagrid.*;
import com.intellij.database.editor.DataGridColors;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.database.util.DataGridUIUtil.softHighlightOf;
import static com.intellij.ui.hover.TableHoverListener.getHoveredRow;

public class SelectionColorLayer implements ColorLayer {
  private final DataGrid myGrid;
  private final boolean myTransparentRowHeaderBg;
  private final boolean myTransparentColumnHeaderBg;

  public SelectionColorLayer(@NotNull DataGrid grid, boolean transparentRowHeaderBg, boolean transparentColumnHeaderBg) {
    myGrid = grid;
    myTransparentRowHeaderBg = transparentRowHeaderBg;
    myTransparentColumnHeaderBg = transparentColumnHeaderBg;
  }

  @Override
  public @Nullable Color getCellBackground(@NotNull ModelIndex<GridRow> row,
                                           @NotNull ModelIndex<GridColumn> column,
                                           @NotNull DataGrid grid,
                                           @Nullable Color color) {
    boolean isRowSelected = grid.getSelectionModel().isSelectedRow(row);

    if (isRowBgPaintedByTable(row, column, grid, isRowSelected)) {
      return color;
    }

    if (isRowSelected) {
      return getSelectedRowColor(grid, color);
    }

    return color;
  }

  static boolean isRowBgPaintedByTable(@NotNull ModelIndex<GridRow> row,
                                              @NotNull ModelIndex<GridColumn> column,
                                              @NotNull DataGrid grid,
                                              boolean selected) {
    ViewIndex<?> viewRow = grid.getResultView().isTransposed() ? column.toView(grid) : row.toView(grid);
    return isRowBgPaintedByTable(viewRow, grid, selected);
  }

  public static boolean isRowBgPaintedByTable(@NotNull ViewIndex<?> viewRow, @NotNull DataGrid grid, boolean selected) {
    return isHoveredRow(viewRow, grid) || isStripedRow(viewRow, grid) && !selected;
  }

  private static boolean isHoveredRow(@NotNull ViewIndex<?> viewRow, @NotNull DataGrid grid) {
    return grid.getResultView().isHoveredRowBgHighlightingEnabled() && grid.getResultView() instanceof JTable &&
           getHoveredRow((JTable)grid.getResultView()) == viewRow.asInteger();
  }

  private static boolean isStripedRow(@NotNull ViewIndex<?> viewRow, @NotNull DataGrid grid) {
    return grid.getResultView().isStriped() &&
           viewRow.asInteger() % 2 == 0;
  }

  @Override
  public @Nullable Color getRowHeaderBackground(@NotNull ModelIndex<GridRow> row, @NotNull DataGrid grid, @Nullable Color color) {
    boolean selected = grid.getSelectionModel().isSelectedRow(row);
    return selected && !isRowBgPaintedByTable(row, ModelIndex.forColumn(grid, -1), grid, selected)
           ? getSelectedRowColor(grid, color)
           : getHeaderColor(color, grid.getResultView().isTransposed() ? myTransparentColumnHeaderBg : myTransparentRowHeaderBg);
  }

  @Override
  public @Nullable Color getColumnHeaderBackground(@NotNull ModelIndex<GridColumn> column, @NotNull DataGrid grid, @Nullable Color color) {
    boolean selected = grid.getSelectionModel().isSelectedColumn(column);
    return selected && !isRowBgPaintedByTable(ModelIndex.forRow(grid, -1), column, grid, selected)
           ? getSelectedColumnColor(grid, color)
           : getHeaderColor(color, grid.getResultView().isTransposed() ? myTransparentRowHeaderBg : myTransparentColumnHeaderBg);
  }

  @Override
  public @NotNull Color getRowHeaderForeground(@NotNull ModelIndex<GridRow> row, @NotNull DataGrid grid, @Nullable Color color) {
    if (grid.getResultView().isTransposed() || !myTransparentRowHeaderBg) {
      return grid.getResultView().getComponent().getForeground();
    }
    Color lineNumbersColor = grid.getColorsScheme().getColor(EditorColors.LINE_NUMBERS_COLOR);
    Color c = grid.getSelectionModel().isSelectedRow(row)
                   ? grid.getColorsScheme().getColor(EditorColors.LINE_NUMBER_ON_CARET_ROW_COLOR)
                   : lineNumbersColor;
    return ObjectUtils.notNull(ObjectUtils.chooseNotNull(c, lineNumbersColor), grid.getResultView().getComponent().getForeground());
  }

  @Override
  public @NotNull Color getColumnHeaderForeground(@NotNull ModelIndex<GridColumn> column, @NotNull DataGrid grid, @Nullable Color color) {
    return grid.getResultView().getComponent().getForeground();
  }

  @Override
  public int getPriority() {
    return 3;
  }

  private @Nullable Color getHeaderColor(@Nullable Color color, boolean transparent) {
    return color == null
           ? transparent
             ? null
             : softHighlightOf(myGrid.getResultView().getComponent().getBackground())
           : color;
  }

  public @Nullable Color getHeaderColor(@Nullable Color color) {
    return getHeaderColor(
      color,
      myGrid.getResultView().isTransposed() ? myTransparentRowHeaderBg : myTransparentColumnHeaderBg
    );
  }

  static @Nullable Color getSelectedRowColor(@NotNull DataGrid grid, @Nullable Color color) {
    return color == null ? getCurrentColor(grid) : softHighlightOf(color);
  }

  static @Nullable Color getSelectedColumnColor(@NotNull DataGrid grid, @Nullable Color color) {
    return color == null ? getCurrentColor(grid) : color;
  }

  private static @Nullable Color getCurrentColor(@NotNull DataGrid grid) {
    return grid.getResultView().isStriped()
           ? grid.getHoveredRowBackground()
           : grid.getColorsScheme().getColor(DataGridColors.GRID_CURRENT_ROW);
  }
}
