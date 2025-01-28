package com.intellij.database.datagrid;

import com.intellij.database.connection.throwable.info.ErrorInfo;
import com.intellij.database.run.ui.MutationSupport;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

public interface GridDataHookUp<Row, Column> extends MutationSupport<Row, Column> {

  @NotNull
  Project getProject();

  @NotNull
  GridPagingModel<Row, Column> getPageModel();

  void updateFilterSortFully();

  @NotNull
  Language getFilterSortLanguage();

  boolean isFilterApplicable();

  @NotNull
  String getFilterPrefix();

  @NotNull
  String getFilterEmptyText();

  @Nullable
  GridFilteringModel getFilteringModel();

  @NotNull
  String getSortingPrefix();

  @NotNull
  String getSortingEmptyText();

  @Nullable
  GridSortingModel<Row, Column> getSortingModel();

  @Nullable
  GridMutator<Row, Column> getMutator();

  @NotNull
  GridLoader getLoader();

  int getBusyCount();

  boolean isReadOnly();


  void addRequestListener(@NotNull RequestListener<Row, Column> listener, @NotNull Disposable disposable);

  interface RequestListener<Row, Column> extends EventListener {

    void error(@NotNull GridRequestSource source, @NotNull ErrorInfo errorInfo);

    void updateCountReceived(@NotNull GridRequestSource source, int updateCount);

    //todo: implemented only for scripted hookup
    @ApiStatus.Experimental
    default void requestStarted(@NotNull GridRequestSource source) {}
    @ApiStatus.Experimental
    default void requestProgress(@NotNull GridRequestSource source, @NlsContexts.ProgressText @Nullable String progress) {}
    void requestFinished(@NotNull GridRequestSource source, boolean success);
  }
}
