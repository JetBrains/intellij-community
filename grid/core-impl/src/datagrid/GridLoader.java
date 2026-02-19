package com.intellij.database.datagrid;

import org.jetbrains.annotations.NotNull;

public interface GridLoader {

  void reloadCurrentPage(@NotNull GridRequestSource source);

  void loadNextPage(@NotNull GridRequestSource source);

  void loadPreviousPage(@NotNull GridRequestSource source);

  void loadLastPage(@NotNull GridRequestSource source);

  void loadFirstPage(@NotNull GridRequestSource source);

  void load(@NotNull GridRequestSource source, int offset);

  void updateTotalRowCount(@NotNull GridRequestSource source);

  void applyFilterAndSorting(@NotNull GridRequestSource source);

  void updateIsTotalRowCountUpdateable();
}
