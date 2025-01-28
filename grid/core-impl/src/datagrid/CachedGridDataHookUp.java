package com.intellij.database.datagrid;

import com.intellij.database.run.ui.grid.GridMutationModel;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class CachedGridDataHookUp extends GridDataHookUpBase<GridRow, GridColumn> {
  private final DataGridListModel myModel;
  private final GridMutationModel myMutationModel;

  public CachedGridDataHookUp(@NotNull Project project, @NotNull DataGridListModel model) {
    super(project);
    myModel = model;
    myMutationModel = new GridMutationModel(this);
  }

  @Override
  public @NotNull GridPagingModel<GridRow, GridColumn> getPageModel() {
    return new GridPagingModelImpl.SinglePage<>(myModel);
  }

  @Override
  public @NotNull GridLoader getLoader() {
    return new GridLoader() {
      @Override
      public void reloadCurrentPage(@NotNull GridRequestSource source) {

      }

      @Override
      public void loadNextPage(@NotNull GridRequestSource source) {

      }

      @Override
      public void loadPreviousPage(@NotNull GridRequestSource source) {

      }

      @Override
      public void loadLastPage(@NotNull GridRequestSource source) {

      }

      @Override
      public void loadFirstPage(@NotNull GridRequestSource source) {

      }

      @Override
      public void load(@NotNull GridRequestSource source, int offset) {

      }

      @Override
      public void updateTotalRowCount(@NotNull GridRequestSource source) {

      }

      @Override
      public void applyFilterAndSorting(@NotNull GridRequestSource source) {

      }

      @Override
      public void updateIsTotalRowCountUpdateable() {

      }
    };
  }

  @Override
  public @NotNull GridModel<GridRow, GridColumn> getMutationModel() {
    return myMutationModel;
  }

  @Override
  public @NotNull GridModel<GridRow, GridColumn> getDataModel() {
    return myModel;
  }
}
