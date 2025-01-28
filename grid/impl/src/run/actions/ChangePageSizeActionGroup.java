package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.FloatingPagingManager;
import com.intellij.database.settings.DataGridSettings;
import com.intellij.database.util.DataGridUIUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.ui.configuration.actions.AlignedIconWithTextAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.intellij.database.datagrid.GridPagingModel.UNLIMITED_PAGE_SIZE;
import static com.intellij.database.datagrid.GridPagingModel.UNSET_PAGE_SIZE;
import static com.intellij.database.datagrid.GridUtil.getSettings;
import static com.intellij.database.datagrid.GridUtil.hidePageActions;

public class ChangePageSizeActionGroup extends DefaultActionGroup implements CustomComponentAction, DumbAware {
  private static final List<Integer> DEFAULT_PAGE_SIZES = Arrays.asList(10, 100, 500, 1000);
  private static final Key<Integer> PAGE_SIZE_KEY = new Key<>("DATA_GRID_PAGE_SIZE_KEY");
  private static final Key<Boolean> SHOW_COUNT_ALL_ACTION_KEY = new Key<>("DATA_GRID_SHOW_COUNT_ALL_ACTION_KEY");

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  public ChangePageSizeActionGroup() {
    setPopup(true);
    setActions(DEFAULT_PAGE_SIZES, false);
  }

  private void setActions(List<Integer> sizes, boolean isSinglePage) {
    removeAll();

    if (isSinglePage) {
      add(new MyCountRowsAction());
    }

    addSeparator(DataGridBundle.message("separator.page.size"));

    for (Integer pageSize : sizes) {
      add(new ChangePageSizeAction(pageSize));
    }
    add(new ChangePageSizeAction(UNLIMITED_PAGE_SIZE));
    add(new SetCustomPageSizeAction());
    add(new Separator());
    add(new SetDefaultPageSizeAction());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (grid == null || grid.getDataHookup() instanceof DocumentDataHookUp) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    if (grid.getDataHookup().getPageModel() instanceof NestedTableGridPagingModel<GridRow, GridColumn> nestedPageModel &&
        nestedPageModel.isStatic()) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    if (FloatingPagingManager.adjustAction(e) == FloatingPagingManager.AdjustmentResult.HIDDEN) {
      return;
    }

    ChangePageSizeActionState state = getActionState(grid);
    if (hidePageActions(grid, e.getPlace())) {
      e.getPresentation().setVisible(false);
    }
    else {
      e.getPresentation().setVisible(true);
      updatePresentation(state, e.getPresentation(), getSettings(grid));
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Component component = e.getPresentation().getClientProperty(COMPONENT_KEY);
    JBPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(null, this, e.getDataContext(), null, true, null);
    if (component == null) {
      DataGridUIUtil.showPopup(popup, null, e);
      return;
    }
    popup.showUnderneathOf(component);
  }

  static @NotNull String format(long num) {
    return String.format("%,d", num);
  }

  @Override
  public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    return createCustomComponentForResultViewToolbar(this, presentation, place);
  }

  public static @NotNull JComponent createCustomComponentForResultViewToolbar(@NotNull AnAction action,
                                                                              @NotNull Presentation presentation,
                                                                              @NotNull String place) {
    ActionButtonWithText c = new ActionButtonWithText(action, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      @Override
      public Insets getInsets() {
        return new JBInsets(0, 0, 0, 0);
      }
    };
    return AlignedIconWithTextAction.align(c);
  }

  private void updatePresentation(ChangePageSizeActionState state, Presentation presentation, @Nullable DataGridSettings settings) {
    ChangePageSizeActionState oldState = getActionState(presentation);
    if (oldState.equals(state)) return;

    presentation.setText(state.text);
    presentation.setDescription(state.description);
    presentation.setEnabled(state.enabled);
    presentation.putClientProperty(PAGE_SIZE_KEY, state.pageSize);
    presentation.putClientProperty(SHOW_COUNT_ALL_ACTION_KEY, state.showCountAllAction);

    JComponent component = presentation.getClientProperty(COMPONENT_KEY);
    if (component != null) {
      component.setToolTipText(state.tooltip);
      component.repaint();
    }


    List<Integer> pageSizes = new ArrayList<>(DEFAULT_PAGE_SIZES);
    pageSizes.add(GridUtilCore.getPageSize(settings));
    if (state.pageSize > 0) {
      pageSizes.add(state.pageSize * 2);
      int halfSize = state.pageSize / 2;
      if (halfSize > 0) pageSizes.add(halfSize);
      ContainerUtil.removeAll(pageSizes, state.pageSize);
    }
    ContainerUtil.removeDuplicates(pageSizes);
    ContainerUtil.sort(pageSizes);
    setActions(pageSizes, state.showCountAllAction);
  }

  private static @NotNull ChangePageSizeActionState getActionState(@NotNull Presentation presentation) {
    JComponent component = presentation.getClientProperty(COMPONENT_KEY);

    String text = presentation.getText();
    String description = presentation.getDescription();
    String tooltip = component != null ? component.getToolTipText() : null;
    boolean loading = presentation.isEnabled();
    Integer pageSize = presentation.getClientProperty(PAGE_SIZE_KEY);
    if (pageSize == null) pageSize = UNSET_PAGE_SIZE;
    Boolean showCountAllAction = presentation.getClientProperty(SHOW_COUNT_ALL_ACTION_KEY);
    if (showCountAllAction == null) showCountAllAction = false;

    return new ChangePageSizeActionState(text, description, tooltip, loading, pageSize, showCountAllAction);
  }

  private static @NotNull ChangePageSizeActionState getActionState(@NotNull DataGrid grid) {
    GridPagingModel<GridRow, GridColumn> pageModel = grid.getDataHookup().getPageModel();

    int pageStartIdx = pageModel.getPageStart();
    int pageEndIdx = pageModel.getPageEnd();
    long totalRowCount = pageModel.getTotalRowCount();

    boolean rowsWereDeleted = totalRowCount < pageEndIdx;
    boolean isSinglePage = pageModel.isFirstPage() && pageModel.isLastPage() && !rowsWereDeleted;
    String text = isSinglePage ?
                  format(totalRowCount) +
                  " " +
                  (totalRowCount == 1
                   ? DataGridBundle.message("action.Console.TableResult.ChangePageSize.row")
                   : DataGridBundle.message("action.Console.TableResult.ChangePageSize.rows")) :
                  pageEndIdx == 0
                  ? "0 " + DataGridBundle.message("action.Console.TableResult.ChangePageSize.rows")
                  : format(pageStartIdx) + "-" + format(pageEndIdx);

    boolean querying = grid.getDataHookup().getBusyCount() > 0;
    boolean enabled = !querying && grid.isReady();

    String description = DataGridBundle.message("group.Console.TableResult.ChangePageSize.description");
    String tooltip = DataGridBundle.message("group.Console.TableResult.ChangePageSize.description");
    if (!enabled) {
      String unavailableText = querying ? DataGridBundle.message("action.Console.TableResult.ChangePageSize.querying") : "";
      description = unavailableText;
      tooltip = unavailableText;
    }

    boolean showCountRowsAction = isSinglePage && pageModel.isTotalRowCountUpdateable() && !querying && grid.isReady();
    return new ChangePageSizeActionState(text, description, tooltip, enabled, pageModel.getPageSize(), showCountRowsAction);
  }

  private static void updateIsTotalRowCountUpdateable(@NotNull DataGrid grid) {
    grid.getDataHookup().getLoader().updateIsTotalRowCountUpdateable();
  }

  private static class ChangePageSizeActionState {
    final @NlsActions.ActionText String text;
    final @NlsActions.ActionDescription String description;
    final @NlsContexts.Tooltip String tooltip;
    final boolean enabled;
    final int pageSize;
    final boolean showCountAllAction;

    ChangePageSizeActionState(@NlsActions.ActionText String text,
                              @NlsActions.ActionDescription String description,
                              @NlsContexts.Tooltip String tooltip,
                              boolean enabled,
                              int pageSize,
                              boolean showCountAllAction) {
      this.text = text;
      this.description = description;
      this.tooltip = tooltip;
      this.enabled = enabled;
      this.pageSize = pageSize;
      this.showCountAllAction = showCountAllAction;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ChangePageSizeActionState state = (ChangePageSizeActionState)o;
      return enabled == state.enabled &&
             pageSize == state.pageSize &&
             Objects.equals(text, state.text) &&
             Objects.equals(description, state.description) &&
             Objects.equals(tooltip, state.tooltip);
    }

    @Override
    public int hashCode() {
      return Objects.hash(text, description, tooltip, enabled, pageSize);
    }
  }

  private static class MyCountRowsAction extends DumbAwareAction {

    MyCountRowsAction() {
      super(DataGridBundle.message("action.CountRows.text"), DataGridBundle.message("action.CountRows.description"), null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
      if (grid == null) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }
      updateIsTotalRowCountUpdateable(grid);
      GridPagingModel<GridRow, GridColumn> pageModel = grid.getDataHookup().getPageModel();
      if (!pageModel.isTotalRowCountUpdateable()) return;
      CountRowsAction.countRows(grid);
      updateIsTotalRowCountUpdateable(grid);
    }
  }
}
