package com.intellij.database.datagrid;

import org.jetbrains.annotations.NotNull;

public abstract class GridPagingModelImpl<Row, Column> implements GridPagingModel<Row, Column> {
  protected final GridModel<Row, Column> myModel;

  protected GridPagingModelImpl(GridModel<Row, Column> model) {
    myModel = model;
  }

  @Override
  public @NotNull ModelIndex<Row> findRow(int rowDataIdx) {
    int modelRowIdx = rowDataIdx >= getPageStart() && rowDataIdx <= getPageEnd() ? rowDataIdx - getPageStart() : -1;
    return ModelIndex.forRow(myModel, modelRowIdx);
  }


  public static class SinglePage<Row, Column> extends GridPagingModelImpl<Row, Column> {
    public SinglePage(GridModel<Row, Column> model) {
      super(model);
    }

    @Override
    public boolean isFirstPage() {
      return true;
    }

    @Override
    public boolean isLastPage() {
      return true;
    }

    @Override
    public void setPageSize(int pageSize) {
    }

    @Override
    public int getPageSize() {
      return -1;
    }

    @Override
    public boolean pageSizeSet() {
      return false;
    }

    @Override
    public long getTotalRowCount() {
      return myModel.getRowCount();
    }

    @Override
    public boolean isTotalRowCountPrecise() {
      return true;
    }

    @Override
    public boolean isTotalRowCountUpdateable() {
      return false;
    }

    @Override
    public int getPageStart() {
      return 1;
    }

    @Override
    public int getPageEnd() {
      return myModel.getRowCount();
    }
  }
}
