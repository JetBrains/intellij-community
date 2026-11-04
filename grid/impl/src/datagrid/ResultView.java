package com.intellij.database.datagrid;

import com.intellij.database.extractors.DisplayType;
import com.intellij.database.run.ui.ResultViewFactory;
import com.intellij.database.run.ui.grid.GridSearchSession;
import com.intellij.database.run.ui.table.LocalFilterState;
import com.intellij.find.FindModel;
import com.intellij.find.SearchSession;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.List;
import java.util.Map;

public interface ResultView extends CoreResultView, GridModel.Listener<GridRow, GridColumn>, GridSearchSession.Listener, Disposable,
                                    LocalFilterState.Listener, ColumnHierarchyListener {
  /**
   * The view's root, which holds its cells and carries the view's client properties.
   * Focus goes to {@link #getPreferredFocusedComponent()}, not to this component.
   * {@link ResultViewFactory#wrap} returns the component that goes into the grid layout.
   */
  @NotNull
  JComponent getComponent();

  boolean isTransposed();

  void setTransposed(boolean transposed);

  void resetLayout();

  void setColumnEnabled(@NotNull ModelIndex<GridColumn> columnIdx, boolean state);

  default void setColumnsEnabled(@NotNull List<ModelIndex<GridColumn>> columns, boolean state) {
    for (var column : columns) setColumnEnabled(column, state);
  }

  /** Brings the leftmost of {@code columns} into view, leaving the rows where they are. */
  default void scrollColumnsIntoView(@NotNull List<ModelIndex<GridColumn>> columns) {
  }

  void setRowEnabled(@NotNull ModelIndex<GridRow> rowIdx, boolean state);

  void showFirstCell(int rowNumOnCurrentPage);

  void growSelection();

  void shrinkSelection();

  void addSelectionChangedListener(@NotNull ResultViewSelectionListener listener);

  void restoreColumnsOrder(Map<Integer, ModelIndex<GridColumn>> expectedToModel);

  /** Whether the view has an active editor in any component that renders its cells. */
  boolean isEditing();

  /** The row under the mouse pointer, as a row of the view (a grid column while transposed), or -1 when there is none. */
  default int getHoveredRow() {
    return -1;
  }

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

  /** Adds a mouse listener to the components that show the view's cells and to the empty space around them. */
  void addMouseListenerToComponents(@NotNull MouseListener listener);

  boolean supportsCustomSearchSession();

  @Nullable
  SearchSession createSearchSession(@Nullable FindModel findModel, @Nullable Component previousFilterComponent);

  default void searchSessionStarted(@NotNull SearchSession searchSession) { }

  /** The component that takes focus, for focus requests and platform focus APIs only. Ask it at each request and never store it. */
  default @NotNull JComponent getPreferredFocusedComponent() {
    return getComponent();
  }

  /**
   * Whether {@code component} is part of the view: its cells or row numbers, and in some views its headers.
   * The default counts the preferred focused component and the row header of the scroll pane around it, such as an editor's gutter.
   */
  default boolean isViewComponent(@NotNull Component component) {
    JComponent focus = getPreferredFocusedComponent();
    if (SwingUtilities.isDescendingFrom(component, focus)) return true;
    JScrollPane scrollPane = ComponentUtil.getParentOfType(JScrollPane.class, focus);
    JViewport rowHeader = scrollPane == null ? null : scrollPane.getRowHeader();
    return rowHeader != null && SwingUtilities.isDescendingFrom(component, rowHeader);
  }

  /** The foreground of the view's cells and headers. */
  default @NotNull Color getForeground() {
    return getComponent().getForeground();
  }

  /** The background of the view's cells. */
  default @NotNull Color getBackground() {
    return getComponent().getBackground();
  }

  /**
   * Lets the view's cells fill the space they get while {@code noColumns}, and take their own size otherwise. The default sizes
   * {@link #getComponent()}.
   */
  default void updateCellsPreferredSize(boolean noColumns) {
    Container parent = getComponent().getParent();
    if (parent != null) getComponent().setPreferredSize(noColumns ? parent.getSize() : null);
  }

  /**
   * Adds a mouse listener to the components that show the view's cells, without the empty space around them. The default adds it to
   * {@link #getComponent()}.
   */
  default void addMouseListenerToCells(@NotNull MouseListener listener) {
    getComponent().addMouseListener(listener);
  }

  /** Adds a mouse motion listener to the components that show the view's cells. The default adds it to {@link #getComponent()}. */
  default void addMouseMotionListenerToCells(@NotNull MouseMotionListener listener) {
    getComponent().addMouseMotionListener(listener);
  }

  /** Adds a focus listener to the components that take the focus for the view's cells, until {@code parent} goes. */
  default void addFocusListenerToCells(@NotNull FocusListener listener, @NotNull Disposable parent) {
    JComponent cells = getPreferredFocusedComponent();
    cells.addFocusListener(listener);
    Disposer.register(parent, () -> cells.removeFocusListener(listener));
  }

  /**
   * Whether the view has the focus. The default asks {@link #getComponent()}; a view whose focus goes to another component, or to one of
   * several, overrides this.
   */
  default boolean hasFocus() {
    return getComponent().hasFocus();
  }

  /** Where a popup or a balloon about the selected cell points, or null when the view has no such place. */
  default @Nullable RelativePoint getBestPopupLocation() {
    return null;
  }

  default void registerEscapeAction(@NotNull AbstractAction action) {
    String actionId = "grid.escape";
    KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    getComponent().getInputMap(JComponent.WHEN_FOCUSED).put(keyStroke, actionId);
    getComponent().getActionMap().put(actionId, action);
  }

  default void defaultBackgroundChanged() { }

  void setValueAt(@Nullable Object v, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column, boolean allowImmediateUpdate, @NotNull GridRequestSource source);

  default void setValueAt(@Nullable Object v,
                          @NotNull ModelIndex<GridRow> row,
                          @NotNull ModelIndex<GridColumn> column,
                          boolean allowImmediateUpdate,
                          @NotNull GridRequestSource source,
                          @Nullable Object metadata) {
    setValueAt(v, row, column, allowImmediateUpdate, source);
  }

  void columnAttributesUpdated();

  void reinitSettings();

  default void setVisibleRowCount(int v) {
  }

  /** Whether the columns share the width of the view instead of keeping their own widths. */
  default void setFitColumnsToWidth(boolean v) {
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
