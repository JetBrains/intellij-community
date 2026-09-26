package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.datagrid.CoreGrid;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridCellRequest;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.openapi.util.Key;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface GridCellEditorFactoryProvider {
  Key<GridCellEditorFactoryProvider> FACTORY_PROVIDER_KEY = new Key<>("FACTORY_PROVIDER_KEY");

  @Nullable
  GridCellEditorFactory getEditorFactory(@NotNull GridCellRequest<GridRow, GridColumn> request);

  static void set(@NotNull DataGrid grid, @Nullable GridCellEditorFactoryProvider provider) {
    set((CoreGrid<GridRow, GridColumn>)grid, provider);
  }
  static void set(@NotNull CoreGrid<GridRow, GridColumn> grid, @Nullable GridCellEditorFactoryProvider provider) {
    grid.putUserData(FACTORY_PROVIDER_KEY, provider);
  }

  static @Nullable GridCellEditorFactoryProvider get(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    return grid.getUserData(FACTORY_PROVIDER_KEY);
  }

  static @Nullable GridCellEditorFactory provideEditorFactory(@NotNull GridCellRequest<GridRow, GridColumn> request) {
    GridCellEditorFactoryProvider provider = get(request.getGrid());
    return provider == null ? null : provider.getEditorFactory(request);
  }

  static <T> T getEditorFactory(@NotNull List<? extends GridCellEditorFactory> factories,
                                @NotNull Function<T, Integer> suitabilityCheck,
                                Class<T> clazz) {
    int maxSuitability = GridCellEditorFactory.SUITABILITY_UNSUITABLE;
    T bestMatchingFactory = null;

    for (GridCellEditorFactory factory : factories) {
      if (!clazz.isAssignableFrom(factory.getClass())) continue;
      //noinspection unchecked
      T f = (T)factory;
      int suitability = suitabilityCheck.fun(f);
      if (suitability > maxSuitability) {
        maxSuitability = suitability;
        bestMatchingFactory = f;
      }
    }

    return bestMatchingFactory;
  }
}
