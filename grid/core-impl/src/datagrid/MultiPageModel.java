package com.intellij.database.datagrid;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface MultiPageModel<Row, Column> extends GridPagingModel<Row, Column> {
  void setPageStart(int pageStart);

  void setPageEnd(int pageEnd);

  void setTotalRowCount(long totalRowCount, boolean precise);

  void setTotalRowCountUpdateable(boolean updateable);

  void addPageModelListener(@NotNull PageModelListener listener);

  interface PageModelListener extends EventListener {
    void pageSizeChanged();
    void pageStartChanged();
  }
}
