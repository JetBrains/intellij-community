package com.intellij.database.datagrid;

import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.GridDataSupport;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolder;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface CoreGrid<Row, Column> extends UserDataHolder, Disposable {
  @NotNull
  GridModel<Row, Column> getDataModel(@NotNull DataAccessType reason);

  @NotNull
  GridDataHookUp<Row, Column> getDataHookup();

  @NotNull
  SelectionModel<Row, Column> getSelectionModel();

  @NotNull
  GridDataSupport getDataSupport();

  @NotNull
  RawIndexConverter getRawIndexConverter();

  @NotNull
  String getFilterText();

  void setFilterText(@NotNull String filter, int caretPosition);

  boolean isReady();

  @NotNull
  CoreResultView getResultView();

  @NotNull
  Project getProject();

  @NotNull
  CoroutineScope getCoroutineScope();

  @NotNull
  JComponent getMainResultViewComponent();

  @NotNull
  JComponent getPreferredFocusedComponent();

  @NotNull
  ModelIndexSet<Column> getVisibleColumns();

  @NotNull
  ModelIndexSet<Row> getVisibleRows();

  int getVisibleRowsCount();

  boolean isViewModified();

  void resetView();

  void showCell(int absoluteRowIdx, @NotNull ModelIndex<Column> column);

  boolean isColumnEnabled(@NotNull ModelIndex<Column> column);

  void setColumnEnabled(@NotNull ModelIndex<Column> column, boolean state);

  void setRowEnabled(@NotNull ModelIndex<Row> rowIdx, boolean state);

  @Nls
  @NotNull
  String getDisplayName();

  boolean isEditable();

  boolean isCellEditingAllowed();

  void setCells(@NotNull ModelIndexSet<Row> rows, @NotNull ModelIndexSet<Column> columns, @Nullable Object value);

  boolean isEditing();

  boolean stopEditing();

  void cancelEditing();

  void editSelectedCell();

  @NotNull
  @NlsSafe
  String getUnambiguousColumnName(@NotNull ModelIndex<Column> column);
}
