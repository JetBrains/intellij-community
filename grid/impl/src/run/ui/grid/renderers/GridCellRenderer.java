package com.intellij.database.run.ui.grid.renderers;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridCellRequest;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ViewIndex;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.Objects;

public abstract class GridCellRenderer implements Disposable {
  public static final int SUITABILITY_UNSUITABLE = 0;
  public static final int SUITABILITY_MIN = 1;
  public static final int SUITABILITY_MAX = 10;

  public final DataGrid myGrid;

  protected GridCellRenderer(@NotNull DataGrid grid) {
    myGrid = grid;
  }

  public abstract int getSuitability(@NotNull GridCellRequest<GridRow, GridColumn> request);

  public abstract @NotNull JComponent getComponent(@NotNull ViewIndex<GridRow> row, @NotNull ViewIndex<GridColumn> column, @Nullable Object value);

  @Override
  public void dispose() {
  }

  public void clearCache() {
  }

  public abstract void reinitSettings();

  public static @NotNull GridCellRenderer getRenderer(@NotNull GridCellRequest<GridRow, GridColumn> request) {
    GridCellRendererFactories factories = GridCellRendererFactories.get((DataGrid)request.getGrid());

    GridCellRenderer bestRenderer = null;
    int bestSuitability = SUITABILITY_UNSUITABLE;

    for (GridCellRendererFactory factory : factories.getFactoriesFor(request)) {
      GridCellRenderer renderer = factory.getOrCreateRenderer(request);
      int suitability = renderer.getSuitability(request);
      if (suitability > bestSuitability) {
        bestRenderer = renderer;
        bestSuitability = suitability;
      }
    }

    return Objects.requireNonNull(bestRenderer);
  }
}
