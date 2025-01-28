package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.*;
import com.intellij.database.settings.DataGridSettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiElement;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;


public abstract class ColumnAction extends DumbAwareAction implements UserDataHolder {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }


  final UserDataHolderBase context = new UserDataHolderBase();

  @Override
  public <T> @Nullable T getUserData(@NotNull Key<T> key) {
    return context.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    context.putUserData(key, value);
  }

  protected boolean availableInTable() {
    return false;
  }

  protected abstract void actionPerformed(DataGrid dataGrid, List<ModelIndex<GridColumn>> columns);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid dataGrid = GridUtil.getDataGrid(e.getDataContext());
    if (dataGrid == null) return;

    List<ModelIndex<GridColumn>> columns = getSelectedColumns(dataGrid, e.getDataContext(), availableInTable());
    if (columns.isEmpty()) return;

    actionPerformed(dataGrid, columns);
  }

  private static @NotNull List<ModelIndex<GridColumn>> getSelectedColumns(@Nullable DataGrid dataGrid,
                                                                          @NotNull DataContext dataContext,
                                                                          boolean allowInTable) {
    PsiElement[] selectedPsi = PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    if (allowInTable && dataGrid != null && dataGrid.getContextColumn().asInteger() != -1) {
      return Collections.singletonList(dataGrid.getContextColumn());
    }
    if (selectedPsi == null) {
      return allowInTable && dataGrid != null ?
             dataGrid.getSelectionModel().getSelectedColumns().asIterable().map(c -> c).toList() :
             Collections.emptyList();
    }
    List<ModelIndex<GridColumn>> result = new ArrayList<>(selectedPsi.length);
    for (PsiElement element : selectedPsi) {
      DataGridPomTarget.Column target = DataGridPomTarget.unwrapColumn(element);
      if (target != null && target.dataGrid == dataGrid) {
        result.add(target.column);
        continue;
      }
      DataGridPomTarget.Cell cell = allowInTable ? DataGridPomTarget.unwrapCell(element) : null;
      if (cell != null && cell.dataGrid == dataGrid) {
        cell.columns.asIterable().map(c -> c).addAllTo(result);
      }
    }
    return result;
  }

  public static class Visibility extends ColumnAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      DataGrid dataGrid = GridUtil.getDataGrid(e.getDataContext());

      boolean enabled = false;
      if (dataGrid != null) {
        List<ModelIndex<GridColumn>> columns = getSelectedColumns(dataGrid, e.getDataContext(), availableInTable());
        if (!columns.isEmpty()) {
          enabled = isEnabled(dataGrid, columns, e);
        }
      }
      e.getPresentation().setEnabledAndVisible(enabled);
    }

    private static boolean isEnabled(DataGrid dataGrid, List<ModelIndex<GridColumn>> columns, AnActionEvent e) {
      boolean toShow = toShow(dataGrid, columns);
      boolean single = columns.size() == 1;
      e.getPresentation().setText((toShow
                                   ? DataGridBundle.message("action.ColumnAction.Visibility.show.text")
                                   : DataGridBundle.message("action.ColumnAction.Visibility.hide.text"))
                                  + " " +
                                  (single
                                   ? DataGridBundle.message("action.ColumnAction.Visibility.column.text")
                                   : DataGridBundle.message("action.ColumnAction.Visibility.columns.text")));
      return true;
    }

    @Override
    protected void actionPerformed(DataGrid dataGrid, List<ModelIndex<GridColumn>> columns) {
      boolean toShow = toShow(dataGrid, columns);
      for (ModelIndex<GridColumn> c : columns) {
        dataGrid.setColumnEnabled(c, toShow);
      }
    }

    private static boolean toShow(DataGrid dataGrid, List<ModelIndex<GridColumn>> columns) {
      ModelIndex<GridColumn> column = ContainerUtil.getFirstItem(columns);
      return column != null && !dataGrid.isColumnEnabled(column);
    }
  }

  private abstract static class SortAction extends ColumnAction {
    protected final RowSortOrder.Type mySortOrder;
    private final boolean myAdditive;
    private final Supplier<@Nls String> myMenuText;

    SortAction(@NotNull RowSortOrder.Type sortOrder, boolean additive) {
      this(sortOrder, additive, null);
    }

    SortAction(@NotNull RowSortOrder.Type sortOrder, boolean additive, @Nullable Supplier<@Nls String> menuText) {
      mySortOrder = sortOrder;
      myAdditive = additive;
      myMenuText = menuText;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      DataGrid dataGrid = GridUtil.getDataGrid(e.getDataContext());
      if (dataGrid == null || !GridHelper.get(dataGrid).isSortingApplicable() ||
          dataGrid.getResultView().isTransposed() && !dataGrid.isSortViaOrderBy()) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }
      List<ModelIndex<GridColumn>> selectedColumns = getSelectedColumns(dataGrid, e.getDataContext(), availableInTable());
      List<ModelIndex<GridColumn>> columns = getNotInserted(dataGrid, selectedColumns);
      if (columns.isEmpty()) {
        e.getPresentation().setVisible(!selectedColumns.isEmpty());
        e.getPresentation().setEnabled(false);
        return;
      }
      if (ContainerUtil.exists(selectedColumns, colIdx -> !GridHelper.get(dataGrid).isSortingApplicable(colIdx))) {
        e.getPresentation().setVisible(true);
        e.getPresentation().setEnabled(false);
        return;
      }
      e.getPresentation().setVisible(true);
      boolean newOrder = !ContainerUtil.all(columns, column -> dataGrid.getSortOrder(column) == mySortOrder);
      boolean otherColumnsSorted = !GridUtil.areOnlySortedColumns(columns, dataGrid);
      if (myAdditive) {
        GridHelper gridHelper = GridHelper.get(dataGrid);
        GridSortingModel<GridRow, GridColumn> model = dataGrid.getDataHookup().getSortingModel();
        if (model != null &&
            !gridHelper.canSortTogether(dataGrid, ContainerUtil.map(model.getAppliedOrdering(), order -> order.getColumn()), columns)) {
          e.getPresentation().setEnabled(false);
        }
        else if (model == null || !model.isSortingEnabled() || model.supportsAdditiveSorting()) {
          e.getPresentation().setEnabled(newOrder && otherColumnsSorted);
        }
        else {
          e.getPresentation().setEnabledAndVisible(false);
        }
      }
      else e.getPresentation().setEnabled(newOrder || otherColumnsSorted);

      if (e.getPlace().equals(ActionPlaces.POPUP) && myMenuText != null) e.getPresentation().setText(myMenuText.get());
      else e.getPresentation().setText(getTemplateText());

      updateShortcutText(dataGrid);
    }

    protected static @NotNull List<ModelIndex<GridColumn>> getNotInserted(@Nullable DataGrid dataGrid,
                                                                          @NotNull List<ModelIndex<GridColumn>> columns) {
      GridMutator.ColumnsMutator<GridRow, GridColumn> mutator = GridUtil.getColumnsMutator(dataGrid);
      return mutator == null ? columns : ContainerUtil.filter(columns, column -> !mutator.isInsertedColumn(column));
    }

    private void updateShortcutText(@NotNull DataGrid grid) {
      if (mySortOrder == RowSortOrder.Type.UNSORTED) return;
      ModelIndex<GridColumn> contextColumn = grid.getContextColumn();
      if (contextColumn.asInteger() == -1 ||
          grid.getSortOrder(contextColumn) != RowSortOrder.Type.UNSORTED ||
          getShortcutSet().hasShortcuts()) {
        putUserData(PopupListElementRenderer.CUSTOM_KEY_STROKE_TEXT, null);
        return;
      }
      DataGridSettings settings = GridUtil.getSettings(grid);
      boolean useAltKey = myAdditive == (settings == null || settings.isAddToSortViaAltClick());
      MouseShortcut shortcut = new MouseShortcut(MouseEvent.BUTTON1,
                                                 useAltKey ? InputEvent.ALT_DOWN_MASK : 0,
                                                 mySortOrder == RowSortOrder.Type.ASC ? 1 : 2);
      @NlsSafe String mouseShortcut = KeymapUtil.getMouseShortcutText(shortcut);
      putUserData(PopupListElementRenderer.CUSTOM_KEY_STROKE_TEXT, mouseShortcut);
    }

    @Override
    protected boolean availableInTable() {
      return true;
    }

    @Override
    protected void actionPerformed(DataGrid dataGrid, List<ModelIndex<GridColumn>> columns) {
      dataGrid.sortColumns(getNotInserted(dataGrid, columns), mySortOrder, myAdditive);
    }
  }

  public static class SortAsc extends SortAction {
    public SortAsc() {
      super(RowSortOrder.Type.ASC, false);
    }
  }

  public static class SortAddAsc extends SortAction {
    public SortAddAsc() {
      super(RowSortOrder.Type.ASC, true, DataGridBundle.messagePointer("action.Console.TableResult.ColumnSortAsc.text"));
    }
  }

  public static class SortDesc extends SortAction {
    public SortDesc() {
      super(RowSortOrder.Type.DESC, false);
    }
  }

  public static class SortAddDesc extends SortAction {
    public SortAddDesc() {
      super(RowSortOrder.Type.DESC, true, DataGridBundle.messagePointer("action.Console.TableResult.ColumnSortDesc.text"));
    }
  }

  public static class SortReset extends SortAction {
    public SortReset() {
      super(RowSortOrder.Type.UNSORTED, false);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      DataGrid dataGrid = GridUtil.getDataGrid(e.getDataContext());
      if (dataGrid == null) return;
      List<ModelIndex<GridColumn>> selectedColumns = getSelectedColumns(dataGrid, e.getDataContext(), availableInTable());
      List<ModelIndex<GridColumn>> columns = getNotInserted(dataGrid, selectedColumns);
      boolean sameOrder = ContainerUtil.all(columns, column -> dataGrid.getSortOrder(column) == mySortOrder);
      e.getPresentation().setEnabled(!sameOrder);
    }

    @Override
    protected void actionPerformed(DataGrid dataGrid, List<ModelIndex<GridColumn>> columns) {
      dataGrid.sortColumns(getNotInserted(dataGrid, columns), mySortOrder, true);
    }
  }
}
