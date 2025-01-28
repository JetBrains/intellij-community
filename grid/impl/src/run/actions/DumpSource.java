package com.intellij.database.run.actions;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.run.actions.DumpSourceNameProvider.DataGridSourceNameProvider;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Liudmila Kornilova
 **/
public interface DumpSource<T> {
  @NotNull
  JBIterable<T> getSources();

  @NotNull DumpSourceNameProvider<T> getNameProvider();

  default int estimateSize() {
    JBIterable<T> sources = getSources();
    return sources.isEmpty() ? 0 :
           sources.skip(1).isEmpty() ? 1 : 2;
  }

  static int getSize(@Nullable DumpSource<?> object) {
    return object == null ? 1 : object.estimateSize();
  }

  class DataGridSource implements DumpSource<DataGrid> {
    private final DataGrid myGrid;

    public DataGridSource(DataGrid grid) {
      myGrid = grid;
    }

    public @NotNull DataGrid getGrid() {
      return myGrid;
    }

    @Override
    public @NotNull JBIterable<DataGrid> getSources() {
      return JBIterable.of(myGrid);
    }

    @Override
    public @NotNull DumpSourceNameProvider<DataGrid> getNameProvider() {
      return DataGridSourceNameProvider.INSTANCE;
    }
  }
}
