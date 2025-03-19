package com.intellij.database.datagrid.color;

import com.intellij.database.datagrid.*;
import com.intellij.database.datagrid.mutating.MutationData;
import com.intellij.database.run.ui.grid.CellAttributes;
import com.intellij.database.run.ui.grid.CellAttributesKey;
import com.intellij.database.run.ui.grid.editors.UnparsedValue;
import com.intellij.openapi.ui.MessageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class MutationsColorLayer implements ColorLayer {

  private final GridMutator.DatabaseMutator<GridRow, GridColumn> myMutator;

  public MutationsColorLayer(@Nullable GridMutator.DatabaseMutator<GridRow, GridColumn> mutator) {
    myMutator = mutator;
  }

  @Override
  public @Nullable Color getCellBackground(@NotNull ModelIndex<GridRow> row,
                                           @NotNull ModelIndex<GridColumn> column,
                                           @NotNull DataGrid grid,
                                           @Nullable Color color) {
    if (myMutator == null) return getColor(grid, null, color);
    MutationData mutation = myMutator.getMutation(row, column);
    if (mutation != null && mutation.getValue() instanceof UnparsedValue) {
      return getFailedToInsertColor();
    }
    MutationType type = myMutator.getMutationType(row, column);
    return getColor(grid, type, color);
  }

  @Override
  public @Nullable Color getRowHeaderBackground(@NotNull ModelIndex<GridRow> row, @NotNull DataGrid grid, @Nullable Color color) {
    if (myMutator != null && myMutator.hasUnparsedValues(row)) return getFailedToInsertColor();
    MutationType type = myMutator == null ? null : myMutator.getMutationType(row);
    return getColor(grid, type, color);
  }

  @Override
  public @Nullable Color getColumnHeaderBackground(@NotNull ModelIndex<GridColumn> column, @NotNull DataGrid grid, @Nullable Color color) {
    return color;
  }

  @Override
  public int getPriority() {
    return 1;
  }

  private @Nullable Color getColor(@NotNull DataGrid grid, @Nullable MutationType type, @Nullable Color oldColor) {
    CellAttributesKey key = type != null ? GridUtil.getMutationCellAttributes(type) : null;
    CellAttributes attributes = key == null ? null : grid.getColorsScheme().getAttributes(key);
    Color bg = attributes == null ? null : attributes.getBackgroundColor();
    return maybeFailed(bg, oldColor);
  }

  private @Nullable Color maybeFailed(@Nullable Color newColor, @Nullable Color oldColor) {
    return newColor == null ? oldColor :
           myMutator != null && myMutator.isFailed() ? getFailedToInsertColor() :
           newColor;
  }

  private static @NotNull Color getFailedToInsertColor() {
    return MessageType.ERROR.getPopupBackground();
  }
}
