package com.intellij.database.datagrid;

import com.intellij.database.connection.throwable.info.ErrorInfo;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.database.util.DataSourceUtilBase.eventDispatcher;

public abstract class GridDataHookUpBase<Row, Column> implements GridDataHookUp<Row, Column> {
  private final Project myProject;
  private final EventDispatcher<GridDataHookUp.RequestListener<Row, Column>> myRequestEventDispatcher =
    eventDispatcher(GridDataHookUp.RequestListener.class);

  protected GridDataHookUpBase(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void addRequestListener(@NotNull RequestListener<Row, Column> listener, @NotNull Disposable disposable) {
    myRequestEventDispatcher.addListener(listener, disposable);
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public void updateFilterSortFully() {
  }

  @Override
  public boolean isFilterApplicable() {
    return false;
  }

  @Override
  public @NotNull Language getFilterSortLanguage() {
    return PlainTextLanguage.INSTANCE;
  }

  @Override
  public @NotNull String getFilterPrefix() {
    return "";
  }

  @Override
  public @NotNull String getFilterEmptyText() {
    return "";
  }

  @Override
  public @Nullable GridFilteringModel getFilteringModel() {
    return null;
  }

  @Override
  public @NotNull String getSortingPrefix() {
    return "";
  }

  @Override
  public @NotNull String getSortingEmptyText() {
    return "";
  }

  @Override
  public @Nullable GridSortingModel<Row, Column> getSortingModel() {
    return null;
  }

  @Override
  public @Nullable GridMutator<Row, Column> getMutator() {
    return null;
  }

  @Override
  public int getBusyCount() {
    return 0;
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  public void notifyRequestStarted(final @NotNull GridRequestSource source) {
    ApplicationManager.getApplication().invokeLater(() -> myRequestEventDispatcher.getMulticaster().requestStarted(source));
  }

  public void notifyRequestFinished(final @NotNull GridRequestSource source, final boolean success) {
    source.requestComplete(success);
    ApplicationManager.getApplication().invokeLater(() -> myRequestEventDispatcher.getMulticaster().requestFinished(source, success));
  }

  public void notifyRequestProgress(final @NotNull GridRequestSource source, @NlsContexts.ProgressText @Nullable String progress) {
    ApplicationManager.getApplication().invokeLater(() -> myRequestEventDispatcher.getMulticaster().requestProgress(source, progress));
  }

  public void notifyRequestError(final @NotNull GridRequestSource source, final @NotNull ErrorInfo errorInfo) {
    source.setErrorOccurred(errorInfo.getMessage());
    ApplicationManager.getApplication().invokeLater(() -> myRequestEventDispatcher.getMulticaster().error(source, errorInfo));
  }

  public void notifyUpdateCountReceived(final @NotNull GridRequestSource source, final int updateCount) {
    ApplicationManager.getApplication()
      .invokeLater(() -> myRequestEventDispatcher.getMulticaster().updateCountReceived(source, updateCount));
  }
}
