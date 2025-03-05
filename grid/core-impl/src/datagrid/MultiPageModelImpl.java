package com.intellij.database.datagrid;

import com.intellij.database.settings.DataGridSettings;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MultiPageModelImpl<Row, Column> extends GridPagingModelImpl<Row, Column> implements MultiPageModel<Row, Column> {
  private final DataGridSettings mySettings;
  private final EventDispatcher<PageModelListener> myPageModelListeners = EventDispatcher.create(PageModelListener.class);
  private int myPageSize = UNSET_PAGE_SIZE;
  private int myPageStart = 1;
  private int myPageEnd;
  private long myTotalRowCount;
  private boolean myTotalRowCountIsPrecise;
  private boolean myTotalRowCountUpdateable;

  public MultiPageModelImpl(@NotNull GridModel<Row, Column> model, @Nullable DataGridSettings settings) {
    super(model);
    mySettings = settings;
  }

  @Override
  public boolean isFirstPage() {
    return myPageStart == 1;
  }

  @Override
  public boolean isLastPage() {
    return myPageEnd == -1 || myPageEnd >= myTotalRowCount && myTotalRowCountIsPrecise;
  }

  @Override
  public void setPageSize(int pageSize) {
    myPageSize = pageSize;
    myPageModelListeners.getMulticaster().pageSizeChanged();
  }

  @Override
  public int getPageSize() {
    return myPageSize == UNSET_PAGE_SIZE ? GridUtilCore.getPageSize(mySettings) : myPageSize;
  }

  @Override
  public boolean pageSizeSet() {
    return myPageSize != UNSET_PAGE_SIZE;
  }

  @Override
  public long getTotalRowCount() {
    return myTotalRowCount;
  }

  @Override
  public boolean isTotalRowCountPrecise() {
    return myTotalRowCountIsPrecise;
  }

  @Override
  public boolean isTotalRowCountUpdateable() {
    return myTotalRowCountUpdateable;
  }

  @Override
  public int getPageStart() {
    return myPageStart;
  }

  @Override
  public int getPageEnd() {
    return myPageEnd;
  }

  @Override
  public void setPageStart(int pageStart) {
    myPageStart = pageStart;
    myPageModelListeners.getMulticaster().pageStartChanged();
  }

  @Override
  public void setPageEnd(int pageEnd) {
    myPageEnd = pageEnd;
  }

  @Override
  public void setTotalRowCount(long totalRowCount, boolean precise) {
    myTotalRowCount = totalRowCount;
    myTotalRowCountIsPrecise = precise;
  }

  @Override
  public void setTotalRowCountUpdateable(boolean updateable) {
    myTotalRowCountUpdateable = updateable;
  }

  @Override
  public void addPageModelListener(@NotNull PageModelListener listener) {
    myPageModelListeners.addListener(listener);
  }
}
