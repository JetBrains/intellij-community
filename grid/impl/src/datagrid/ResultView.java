package com.intellij.database.datagrid;

import com.intellij.database.extractors.DisplayType;
import com.intellij.database.run.ui.grid.GridSearchSession;
import com.intellij.database.run.ui.table.LocalFilterState;
import com.intellij.find.FindModel;
import com.intellij.find.SearchSession;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseListener;
import java.util.Map;
import java.util.function.Consumer;

public interface ResultView extends CoreResultView, GridModel.Listener<GridRow, GridColumn>, GridSearchSession.Listener, Disposable,
                                    LocalFilterState.Listener, ColumnHierarchyListener {
  @NotNull
  JComponent getComponent();

  boolean isTransposed();

  void setTransposed(boolean transposed);

  void resetLayout();

  void setColumnEnabled(@NotNull ModelIndex<GridColumn> columnIdx, boolean state);

  void setRowEnabled(@NotNull ModelIndex<GridRow> rowIdx, boolean state);

  void showFirstCell(int rowNumOnCurrentPage);

  void growSelection();

  void shrinkSelection();

  void addSelectionChangedListener(@NotNull Consumer<Boolean> listener);

  void restoreColumnsOrder(Map<Integer, ModelIndex<GridColumn>> expectedToModel);

  boolean isEditing();

  boolean isMultiEditingAllowed();

  @NotNull
  ModelIndexSet<GridRow> getVisibleRows();

  @NotNull
  ModelIndexSet<GridColumn> getVisibleColumns();

  int getViewColumnCount();

  int getViewRowCount();

  boolean stopEditing();

  void cancelEditing();

  boolean isViewModified();

  void contentLanguageUpdated(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull Language language);

  void displayTypeUpdated(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull DisplayType displayType);

  @NotNull
  ModelIndex<GridColumn> getContextColumn();

  void updateSortKeysFromColumnAttributes();

  void orderingAndVisibilityChanged();

  @NotNull
  RawIndexConverter getRawIndexConverter();

  void addMouseListenerToComponents(@NotNull MouseListener listener);

  boolean supportsCustomSearchSession();

  @Nullable
  SearchSession createSearchSession(@Nullable FindModel findModel, @Nullable Component previousFilterComponent);

  default void searchSessionStarted(@NotNull SearchSession searchSession) { }

  default @NotNull JComponent getPreferredFocusedComponent() {
    return getComponent();
  }

  default void registerEscapeAction(@NotNull AbstractAction action) {
    String actionId = "grid.escape";
    KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    getComponent().getInputMap(JComponent.WHEN_FOCUSED).put(keyStroke, actionId);
    getComponent().getActionMap().put(actionId, action);
  }

  default void defaultBackgroundChanged() { }

  void setValueAt(@Nullable Object v, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column, boolean allowImmediateUpdate, @NotNull GridRequestSource source);

  void columnAttributesUpdated();

  void reinitSettings();

  default void setVisibleRowCount(int v) {
  }

  default void showRowNumbers(boolean v) {
  }

  default void setTransparentColumnHeaderBackground(boolean v) {
  }

  default void setAdditionalRowsCount(int v) {
  }

  default void setShowHorizontalLines(boolean v) {
  }

  default void setShowVerticalLines(boolean v) {
  }

  default void setStriped(boolean v) {
  }

  default boolean getShowHorizontalLines() {
    return false;
  }

  default boolean isStriped() {
    return false;
  }

  default void setAllowMultilineLabel(boolean v) {
  }

  default void addSpaceForHorizontalScrollbar(boolean v) {
  }

  default void expandMultilineRows(boolean v) {
  }

  default void resetScroll() {
  }

  default boolean isHoveredRowBgHighlightingEnabled() {
    return !getShowHorizontalLines() && !isStriped();
  }

  default void setHoveredRowHighlightMode(HoveredRowBgHighlightMode mode) {}

  enum HoveredRowBgHighlightMode {
    HIGHLIGHT,
    NOT_HIGHLIGHT,
    AUTO
  }
}
