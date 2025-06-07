package com.intellij.database.datagrid;

import com.intellij.database.datagrid.color.GridColorModel;
import com.intellij.database.extractors.BinaryDisplayType;
import com.intellij.database.extractors.DatabaseObjectFormatterConfig.DatabaseDisplayObjectFormatterConfig;
import com.intellij.database.extractors.DisplayType;
import com.intellij.database.extractors.ObjectFormatter;
import com.intellij.database.run.ui.HiddenColumnsSelectionHolder;
import com.intellij.database.run.ui.TableResultPanel;
import com.intellij.database.run.ui.grid.GridColorsScheme;
import com.intellij.database.run.ui.grid.GridMarkupModel;
import com.intellij.database.run.ui.grid.GridRowHeader;
import com.intellij.database.run.ui.table.LocalFilterState;
import com.intellij.database.run.ui.table.TableResultView;
import com.intellij.find.SearchSession;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.JBAutoScroller;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseListener;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;


public interface DataGrid extends CoreGrid<GridRow, GridColumn> {
  @Topic.AppLevel Topic<ActiveGridListener> ACTIVE_GRID_CHANGED_TOPIC = new Topic<>(ActiveGridListener.class, Topic.BroadcastDirection.NONE);

  default @Nullable String getUniqueId() {
    return null;
  }

  boolean isEmpty();

  void runWithIgnoreSelectionChanges(Runnable runnable);

  boolean isSafeToReload();

  ActionCallback submit();

  boolean isFilteringSupported();

  boolean isFilteringComponentShown();

  void toggleFilteringComponent();

  boolean isSafeToUpdate(@NotNull ModelIndexSet<GridRow> rows, @NotNull ModelIndexSet<GridColumn> columns, @Nullable Object newValue);

  @NotNull
  GridFilterAndSortingComponent getFilterComponent();

  void resetFilters();

  boolean isSortViaOrderBySupported();

  boolean isSortViaOrderBy();

  void setSortViaOrderBy(boolean sortViaOrderBy);

  @NotNull
  RowSortOrder.Type getSortOrder(@NotNull ModelIndex<GridColumn> column);

  int getThenBySortOrder(@NotNull ModelIndex<GridColumn> column);

  int getVisibleColumnCount();

  void toggleSortColumns(@NotNull List<ModelIndex<GridColumn>> columns, boolean additive);

  void sortColumns(@NotNull List<ModelIndex<GridColumn>> columns, @NotNull RowSortOrder.Type order, boolean additive);

  TableResultPanel.ColumnAttributes getColumnAttributes();

  @NotNull
  Language getContentLanguage(@NotNull ModelIndex<GridColumn> column);

  boolean isRowFilteredOut(@NotNull ModelIndex<?> rowIdx);

  void setContentLanguage(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull Language language);

  void setDisplayType(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull DisplayType displayType);

  @NotNull
  DisplayType getDisplayType(@NotNull ModelIndex<GridColumn> columnIdx);

  @NotNull
  DisplayType getPureDisplayType(@NotNull ModelIndex<GridColumn> columnIdx);

  @NotNull
  BinaryDisplayType getOptimalBinaryDisplayTypeForDetect(@NotNull ModelIndex<GridColumn> columnIdx);

  boolean isDisplayTypeApplicable(@NotNull BinaryDisplayType displayType, @NotNull ModelIndex<GridColumn> columnIdx);

  @NotNull
  ModelIndex<GridColumn> getContextColumn();

  ObjectFormatter getObjectFormatter();

  TreeMap<Integer, GridColumn> getSortOrderMap();

  int countSortedColumns();

  //todo: why is it dynamic? especially why does it accepts grid?
  void setObjectFormatterProvider(@NotNull Function<DataGrid, ObjectFormatter> objectFormatterProvider);

  int getSortOrder(@Nullable GridColumn column);

  void addDataGridListener(DataGridListener listener, Disposable disposable);

  void resetLayout();

  void loadingDelayDisabled();

  @NotNull GridColorModel getColorModel();

  @NotNull JBAutoScroller.AutoscrollLocker getAutoscrollLocker();

  void loadingDelayed();

  HiddenColumnsSelectionHolder getHiddenColumnSelectionHolder();

  @NotNull GridRowHeader createRowHeader(@NotNull TableResultView table);

  void fireValueEdited(@Nullable Object object);

  @Override
  @NotNull
  ResultView getResultView();

  void fireContentChanged(@Nullable GridRequestSource.RequestPlace place);

  void setValueAt(@NotNull ModelIndexSet<GridRow> viewRows,
                  @NotNull ModelIndexSet<GridColumn> viewColumns,
                  @Nullable Object value,
                  boolean allowImmediateUpdate,
                  @Nullable Runnable moveToNextCellRunnable,
                  @NotNull GridRequestSource source);

  boolean isHeaderSelecting();

  void updateSortKeysFromColumnAttributes();

  void setPresentationMode(@NotNull GridPresentationMode presentationMode);

  @NotNull
  GridPresentationMode getPresentationMode();

  @NotNull
  GridPanel getPanel();

  @NotNull
  GridMarkupModel<GridRow, GridColumn> getMarkupModel();

  @NotNull
  GridColorsScheme getColorsScheme();

  @Nullable
  Color getHoveredRowBackground();

  @Nullable
  Color getStripeRowBackground();

  @NotNull
  GridColorsScheme getEditorColorsScheme();

  void searchSessionStarted(@NotNull SearchSession searchSession);

  void searchSessionStopped(@NotNull SearchSession searchSession);

  @Nullable
  SearchSession getSearchSession();

  void trueLayout();

  @Nullable
  Comparator<?> getComparator(@NotNull ModelIndex<GridColumn> columnIdx);

  @NlsSafe
  @NotNull
  String getName(@NotNull GridColumn column);

  default @Nullable HierarchicalColumnsCollapseManager getHierarchicalColumnsCollapseManager() {
    return null;
  }

  void addResultViewMouseListener(@NotNull MouseListener listener);

  @NotNull
  DataGridAppearance getAppearance();

  @NotNull
  LocalFilterState getLocalFilterState();

  void adaptForNewQuery();

  ModificationTracker getModificationTracker();

  @Nullable
  DatabaseDisplayObjectFormatterConfig getFormatterConfig(@NotNull ModelIndex<GridColumn> columnIdx);

  interface ActiveGridListener {
    default void onFilterApplied(@NotNull DataGrid grid) { }

    default void onSortingApplied(@NotNull DataGrid grid) { }

    default void onColumnSortingToggled(@NotNull DataGrid grid) { }

    default void onValueEditorOpened(@NotNull DataGrid grid) { }

    default void onAggregateViewOpened(@NotNull DataGrid grid) { }

    default void onRecordViewOpened(@NotNull DataGrid grid) { }

    default void onExtractToClipboardAction(@NotNull DataGrid grid) { }

    default void onExtractToFileAction(@NotNull DataGrid grid) { }

    default void changed(@NotNull DataGrid grid) { }

    default void closed() { }
  }
}
