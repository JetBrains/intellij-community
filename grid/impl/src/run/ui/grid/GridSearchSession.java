package com.intellij.database.run.ui.grid;

import com.intellij.database.datagrid.ModelIndex;
import com.intellij.find.SearchSession;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.EventListener;

public interface GridSearchSession<Row, Column> extends SearchSession {
  boolean isMatchedCell(@NotNull ModelIndex<Row> rowIdx, @NotNull ModelIndex<Column> columnIdx);

  boolean isFilteringEnabled();

  void addListener(@NotNull Listener listener, @NotNull Disposable parent);

  @Nullable Component getPreviousFilterComponent();

  interface Listener extends EventListener {
    void searchSessionUpdated();
  }
}
