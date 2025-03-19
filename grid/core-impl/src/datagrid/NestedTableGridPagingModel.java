package com.intellij.database.datagrid;

import com.intellij.database.datagrid.NestedTablesDataGridModel.NestedTableCellCoordinate;
import com.intellij.database.datagrid.nested.NestedTablesAware;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;



/**
 * The NestedTableGridPagingModel class is an implementation of the MultiPageModel interface designed to work with
 * a grid model that contains nested tables. It supports pagination and navigation between nested tables.
 *
 * @param <Row>    the type of the row in the grid
 * @param <Column> the type of the column in the grid
 */
public class NestedTableGridPagingModel<Row, Column> implements MultiPageModel<Row, Column>, NestedTablesAware<Void> {
  private final NonEmptyStack<MultiPageModel<Row, Column>> myNestedTablePageModels;

  private final MultiPageModel<Row, Column> myTopLevelPagingModel;

  private MultiPageModel<Row, Column> myCurrentPagingModel;

  private final GridModelWithNestedTables myGridModel;

  private final List<PageModelListener> myListeners;

  public NestedTableGridPagingModel(GridModelWithNestedTables gridModel, MultiPageModel<Row, Column> pageModel) {
    myTopLevelPagingModel = pageModel;
    myGridModel = gridModel;
    myNestedTablePageModels = new NonEmptyStack<>(pageModel);
    myCurrentPagingModel = pageModel;
    myListeners = new ArrayList<>();
  }


  @Override
  public Void enterNestedTable(@NotNull NestedTableCellCoordinate coordinate, @NotNull NestedTable nestedTable) {
    navigateIntoNestedTable(nestedTable);
    return null;
  }

  @Override
  public Void exitNestedTable(int steps) {
    navigateBackFromNestedTable(steps);
    return null;
  }

  /**
   * Navigates into a nested table in the grid paging model.
   *
   * @param newSelectedNestedTable The nested table to navigate into.
   *
   * @deprecated This method is deprecated and marked for removal. Use the {@link #enterNestedTable(NestedTableCellCoordinate, NestedTable)} method instead.
   */
  @Deprecated(forRemoval = true)
  public void navigateIntoNestedTable(@NotNull NestedTable newSelectedNestedTable) {
    myCurrentPagingModel = createPageModel(newSelectedNestedTable);
    myNestedTablePageModels.push(myCurrentPagingModel);
  }

  /**
   * Navigates back from a nested table in the grid paging model.
   * <p>
   * If the given {@code steps} parameter is negative or equals to or greater than
   * the depth of stack of nested tables, an exception is thrown.
   *
   * @param steps The number of steps to navigate back. This parameter
   *              must be a non-negative integer less than the current depth
   *              of {@link #myNestedTablePageModels} stack, i.e., it should be within
   *              the range [0, size).
   *
   * @deprecated This method is deprecated and marked for removal. Use the {@link #exitNestedTable(int)}} method instead.
   */
  @Deprecated(forRemoval = true)
  public void navigateBackFromNestedTable(int steps) {
    myNestedTablePageModels.pop(steps);
    myCurrentPagingModel = myNestedTablePageModels.last();
  }

  public void reset() {
    myCurrentPagingModel = myTopLevelPagingModel;
    myNestedTablePageModels.reset(myTopLevelPagingModel);
  }

  private @NotNull MultiPageModel<Row, Column> createPageModel(@NotNull NestedTable newSelectedNestedTable) {
    //noinspection unchecked
    MultiPageModel<Row, Column> model = newSelectedNestedTable instanceof StaticNestedTable
      ? new StaticMultiPageModel<>((GridModel<Row, Column>) myGridModel, myTopLevelPagingModel)
      : new MultiPageModelImpl<>((GridModel<Row, Column>) myGridModel, null);
    model.setPageSize(myTopLevelPagingModel.getPageSize());
    for (PageModelListener listener : myListeners) {
      model.addPageModelListener(listener);
    }

    return model;
  }

  @Override
  public boolean isFirstPage() {
    return myCurrentPagingModel.isFirstPage();
  }

  @Override
  public boolean isLastPage() {
    return myCurrentPagingModel.isLastPage();
  }

  @Override
  public void setPageSize(int pageSize) {
    myCurrentPagingModel.setPageSize(pageSize);
  }

  @Override
  public int getPageSize() {
    return myCurrentPagingModel.getPageSize();
  }

  @Override
  public long getTotalRowCount() {
    return myCurrentPagingModel.getTotalRowCount();
  }

  @Override
  public boolean isTotalRowCountPrecise() {
    return myCurrentPagingModel.isTotalRowCountPrecise();
  }

  @Override
  public boolean isTotalRowCountUpdateable() {
    return myCurrentPagingModel.isTotalRowCountUpdateable();
  }

  @Override
  public int getPageStart() {
    return myCurrentPagingModel.getPageStart();
  }

  @Override
  public int getPageEnd() {
    return myCurrentPagingModel.getPageEnd();
  }

  @Override
  public @NotNull
  ModelIndex<Row> findRow(int rowNumber) {
    return myCurrentPagingModel.findRow(rowNumber);
  }

  @Override
  public boolean pageSizeSet() {
    return myCurrentPagingModel.pageSizeSet();
  }

  @Override
  public void setPageStart(int pageStart) {
    myCurrentPagingModel.setPageStart(pageStart);
  }

  @Override
  public void setPageEnd(int pageEnd) {
    myCurrentPagingModel.setPageEnd(pageEnd);
  }

  @Override
  public void setTotalRowCount(long totalRowCount, boolean precise) {
    myCurrentPagingModel.setTotalRowCount(totalRowCount, precise);
  }

  @Override
  public void setTotalRowCountUpdateable(boolean updateable) {
    myCurrentPagingModel.setTotalRowCountUpdateable(updateable);
  }

  @Override
  public void addPageModelListener(@NotNull PageModelListener listener) {
    myListeners.add(listener);
    for (MultiPageModel<Row, Column> model : myNestedTablePageModels) {
      model.addPageModelListener(listener);
    }
  }

  public boolean isStatic() {
    return myCurrentPagingModel instanceof NestedTableGridPagingModel.StaticMultiPageModel<Row,Column>;
  }

  /**
   * The StaticMultiPageModel class is an implementation of the MultiPageModel interface designed to work with
   * ArrayBackedNestedTable that does not support pagination. This class behaves as a SinglePage model but implements
   * MultiPageModel for interface compatibility. Consequently, several methods do not perform any operations in this
   * implementation.
   * It is intended to be used together with {@link NestedTableGridPagingModel}
   *
   * @param <Row>    the type of the row in the grid
   * @param <Column> the type of the column in the grid
   */
  public static class StaticMultiPageModel<Row, Column> implements MultiPageModel<Row, Column> {
    private final GridPagingModel<Row, Column> myDelegate;
    private final GridModel<Row, Column> myGridModel;

    public StaticMultiPageModel(GridModel<Row, Column> gridModel, GridPagingModel<Row, Column> pageModel) {
      myDelegate = pageModel;
      myGridModel = gridModel;
    }

    @Override
    public boolean isFirstPage() {
      return isNestedGrid() || myDelegate.isFirstPage();
    }

    private boolean isNestedGrid() {
      return myGridModel instanceof HierarchicalColumnsDataGridModel model && !model.isTopLevelGrid();
    }

    @Override
    public boolean isLastPage() {
      return isNestedGrid() || myDelegate.isLastPage();
    }

    @Override
    public void setPageSize(int pageSize) {
      if (!isNestedGrid()) myDelegate.setPageSize(pageSize);
    }

    @Override
    public int getPageSize() {
      return isNestedGrid() ? myGridModel.getRowCount() : myDelegate.getPageSize();
    }

    @Override
    public long getTotalRowCount() {
      return isNestedGrid()
             ? myGridModel.getRowCount()
             : myDelegate.getTotalRowCount();
    }

    @Override
    public boolean isTotalRowCountPrecise() {
      return isNestedGrid() || myDelegate.isTotalRowCountPrecise();
    }

    @Override
    public boolean isTotalRowCountUpdateable() {
      return isNestedGrid() || myDelegate.isTotalRowCountUpdateable();
    }

    @Override
    public int getPageStart() {
      return isNestedGrid() ? 1 : myDelegate.getPageStart();
    }

    @Override
    public int getPageEnd() {
      return isNestedGrid() ? myGridModel.getRowCount() : myDelegate.getPageEnd();
    }

    @Override
    public @NotNull
    ModelIndex<Row> findRow(int rowNumber) {
      return isNestedGrid()
             ? ModelIndex.forRow(myGridModel, rowNumber - 1)
             : myDelegate.findRow(rowNumber);
    }

    @Override
    public boolean pageSizeSet() {
      return isNestedGrid() || myDelegate.pageSizeSet();
    }

    @Override
    public void setPageStart(int pageStart) { }

    @Override
    public void setPageEnd(int pageEnd) { }

    @Override
    public void setTotalRowCount(long totalRowCount, boolean precise) { }

    @Override
    public void setTotalRowCountUpdateable(boolean updateable) { }

    @Override
    public void addPageModelListener(@NotNull PageModelListener listener) { }
  }
}
