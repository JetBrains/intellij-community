package com.intellij.database.datagrid;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeFragment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CoreGridHelper {
  void setFilterText(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull String text, int caretPosition);

  @Nullable
  Language getCellLanguage(@NotNull CoreGrid<GridRow, GridColumn> grid,
                           @NotNull ModelIndex<GridRow> row,
                           @NotNull ModelIndex<GridColumn> column,
                           @Nullable Object value);

  boolean canMutateColumns(@NotNull CoreGrid<GridRow, GridColumn> grid);

  @Nullable
  PsiCodeFragment createCellCodeFragment(@NotNull String text,
                                         @NotNull Project project,
                                         @NotNull CoreGrid<GridRow, GridColumn> grid,
                                         @NotNull ModelIndex<GridRow> row,
                                         @NotNull ModelIndex<GridColumn> column,
                                         @Nullable Object value);

  /**
   * Creates a code fragment for expression mode editing.
   * Unlike {@link #createCellCodeFragment}, this parses the text as an expression, not a statement.
   */
  default @Nullable PsiCodeFragment createExpressionCodeFragment(@NotNull String text,
                                                                  @NotNull Project project,
                                                                  @NotNull CoreGrid<GridRow, GridColumn> grid) {
    return null;
  }

  default boolean isModifyColumnAcrossCollection() {
    return false;
  }

  boolean isMixedTypeColumns(@NotNull CoreGrid<GridRow, GridColumn> grid);

  boolean isSortingApplicable();

  default boolean isSortingApplicable(@NotNull ModelIndex<GridColumn> colIdx) {
    return isSortingApplicable();
  }
}
