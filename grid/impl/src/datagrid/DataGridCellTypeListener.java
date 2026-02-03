package com.intellij.database.datagrid;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Key;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface DataGridCellTypeListener extends EventListener {
  Key<EventDispatcher<DataGridCellTypeListener>> GRID_CELL_TYPE_DISPATCHER_KEY = Key.create("GRID_CELL_TYPE_DISPATCHER_KEY");

  void onCellTypeChanged(@NotNull ModelIndexSet<GridRow> rows,
                         @NotNull ModelIndexSet<GridColumn> columns);

  static void addDataGridListener(@NotNull DataGrid grid, @NotNull DataGridCellTypeListener listener, @NotNull Disposable disposable) {
    EventDispatcher<DataGridCellTypeListener> dispatcher = grid.getUserData(GRID_CELL_TYPE_DISPATCHER_KEY);
    if (dispatcher == null) {
      dispatcher = EventDispatcher.create(DataGridCellTypeListener.class);
      grid.putUserData(GRID_CELL_TYPE_DISPATCHER_KEY, dispatcher);
    }
    dispatcher.addListener(listener, disposable);
  }

  static void onCellTypeChanged(@NotNull DataGrid grid, @NotNull ModelIndexSet<GridRow> rows, @NotNull ModelIndexSet<GridColumn> columns) {
    EventDispatcher<DataGridCellTypeListener> dispatcher = grid.getUserData(GRID_CELL_TYPE_DISPATCHER_KEY);
    if (dispatcher != null) {
      dispatcher.getMulticaster().onCellTypeChanged(rows, columns);
    }
  }
}
