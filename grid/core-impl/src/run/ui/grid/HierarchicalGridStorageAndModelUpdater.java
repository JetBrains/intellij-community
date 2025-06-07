package com.intellij.database.run.ui.grid;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.*;
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.ColumnNamesHierarchyNode;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.database.run.ui.grid.GridModelUpdaterUtil.getColumnsIndicesRange;
import static java.lang.String.format;

public class HierarchicalGridStorageAndModelUpdater implements GridModelUpdater {
  private final GridModelUpdater myGridModelUpdater;

  private final HierarchicalColumnsDataGridModel myModel;

  private final GridMutationModel myMutationModel;

  private final Project myProject;

  private final int myColumnsLimit;

  public HierarchicalGridStorageAndModelUpdater(
    @NotNull HierarchicalColumnsDataGridModel model,
    @NotNull GridMutationModel mutationModel,
    @Nullable MutationsStorage storage,
    @Nullable Project project
  ) {
    myMutationModel = mutationModel;
    myGridModelUpdater = new GridStorageAndModelUpdater(model, mutationModel, storage);
    myModel = model;
    myProject = project;
    myColumnsLimit = Registry.intValue("grid.tables.columns.limit", 2000);
  }

  public HierarchicalGridStorageAndModelUpdater(
    @NotNull HierarchicalColumnsDataGridModel model,
    @NotNull GridMutationModel mutationModel,
    @Nullable MutationsStorage storage
  ) {
    myMutationModel = mutationModel;
    myGridModelUpdater = new GridStorageAndModelUpdater(model, mutationModel, storage);
    myModel = model;
    myProject = null;
    myColumnsLimit = Registry.intValue("grid.tables.columns.limit", 2000);
  }

  @Override
  public void removeRows(int firstRowIndex, int rowCount) {
    myGridModelUpdater.removeRows(firstRowIndex, rowCount);
  }

  @Override
  public void setColumns(@NotNull List<? extends GridColumn> columns) {
    myGridModelUpdater.setColumns(columns);
  }

  @Override
  public void setRows(int firstRowIndex, @NotNull List<? extends GridRow> rows, @NotNull GridRequestSource source) {
    myGridModelUpdater.setRows(firstRowIndex, rows, source);
    if (firstRowIndex == 0) {
      boolean typesUpdated = myModel.updateColumnTypes();
      if (!typesUpdated) return;
      // This call is necessary to force the recreation of columns in TableResultView after updating the types of columns in the hierarchy.
      // This logic is not optimal and is planned to be fixed after KTNB-374.
      myMutationModel.notifyColumnsAdded(
        ModelIndexSet.forColumns(myMutationModel, getColumnsIndicesRange(0, myMutationModel.getColumnCount()))
      );
    }
  }
  @Override
  public void addRows(List<? extends GridRow> rows) {
    myGridModelUpdater.addRows(rows);
  }

  @Override
  public void afterLastRowAdded() {
    myGridModelUpdater.afterLastRowAdded();
  }

  public void setColumns(@NotNull List<? extends GridColumn> columns, ColumnNamesHierarchyNode root) {
    myModel.setOriginalColumnNamesHierarchy(root);
    if (myModel.getColumnCount() >= myColumnsLimit) {
      NotificationGroupManager.getInstance().getNotificationGroup("Kotlin Notebook output error")
        .createNotification(
          DataGridBundle.message("hierarchical.grid.too.many.columns.error"),
          DataGridBundle.message("notification.content.could.not.display.table.with.d.columns", myModel.getColumnCount()),
          NotificationType.WARNING
        )
        .notify(myProject);
      throw new TooManyColumnsException(format("Attempt to create grid with %d columns", myModel.getColumnCount()));
    }
    myGridModelUpdater.setColumns(columns);
  }

  public static class TooManyColumnsException extends RuntimeException {
    public TooManyColumnsException(String message) {
      super(message);
    }
  }
}
