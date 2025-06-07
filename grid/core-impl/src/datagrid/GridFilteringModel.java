package com.intellij.database.datagrid;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.List;

public interface GridFilteringModel {

  @NotNull
  String getFilterText();

  @NotNull
  String getAppliedText();

  boolean isIgnoreCurrentText();

  void setIgnoreCurrentText(boolean ignore);

  void setFilterText(@NotNull String text);

  void setHistory(@NotNull List<String> history);

  @NotNull
  List<String> getHistory();

  @NotNull
  Document getFilterDocument();

  void addListener(@NotNull Listener l, @NotNull Disposable disposable);

  void applyCurrentText();

  interface Listener extends EventListener {
    default void onPsiUpdated() { }
    default void onPrefixUpdated() { }
    default void onApplicableUpdated() { }
  }
}
