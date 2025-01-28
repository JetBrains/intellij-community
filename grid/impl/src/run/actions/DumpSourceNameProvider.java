package com.intellij.database.run.actions;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Liudmila Kornilova
 **/
public interface DumpSourceNameProvider<T> {
  int MAX_SYMBOLS = 40;

  @Nullable
  String getName(@NotNull T source);

  @Nullable
  String getQueryText(@NotNull T source);

  final class DataGridSourceNameProvider implements DumpSourceNameProvider<DataGrid> {
    public static final DataGridSourceNameProvider INSTANCE = new DataGridSourceNameProvider();

    private DataGridSourceNameProvider() {
    }

    @Override
    public @Nullable String getName(@NotNull DataGrid source) {
      return GridHelper.get(source).getNameForDump(source);
    }

    @Override
    public @Nullable String getQueryText(@NotNull DataGrid source) {
      return GridHelper.get(source).getQueryText(source);
    }
  }
}
