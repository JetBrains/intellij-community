package com.intellij.database.datagrid;

import org.jetbrains.annotations.NotNull;

public interface GridPagingModel<Row, Column> {
  int UNLIMITED_PAGE_SIZE = -1;
  int UNSET_PAGE_SIZE = -2;

  boolean isFirstPage();

  boolean isLastPage();

  void setPageSize(int pageSize);

  int getPageSize();

  long getTotalRowCount();

  boolean isTotalRowCountPrecise();

  boolean isTotalRowCountUpdateable();

  int getPageStart();

  int getPageEnd();

  @NotNull
  ModelIndex<Row> findRow(int rowDataIdx);

  boolean pageSizeSet();
}
