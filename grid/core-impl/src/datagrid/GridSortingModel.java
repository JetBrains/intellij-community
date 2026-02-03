package com.intellij.database.datagrid;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;
import java.util.List;

public interface GridSortingModel<Row, Column> {

  boolean isSortingEnabled();

  default boolean replacesClientSort() {
    return false;
  }

  void setSortingEnabled(boolean enabled);

  List<RowSortOrder<ModelIndex<Column>>> getOrdering();

  void setOrdering(@NotNull List<RowSortOrder<ModelIndex<Column>>> ordering);

  @NotNull
  List<RowSortOrder<ModelIndex<Column>>> getAppliedOrdering();

  @NotNull
  String getAppliedSortingText();

  void apply();

  @Nullable
  Document getDocument();

  @NotNull
  List<String> getHistory();

  void setHistory(@NotNull List<String> history);

  void addListener(@NotNull Listener l, @NotNull Disposable disposable);

  boolean supportsAdditiveSorting();

  interface Listener extends EventListener {
    default void orderingChanged() { }
    default void onPsiUpdated() { }
    default void onPrefixUpdated() { }
  }
}
