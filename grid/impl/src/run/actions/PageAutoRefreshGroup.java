package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.DocumentDataHookUp;
import com.intellij.database.datagrid.ImmutableDataHookUp;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.Formats;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BadgeIconSupplier;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.intellij.database.datagrid.GridUtil.hidePageActions;

public class PageAutoRefreshGroup extends ActionGroup implements GridAction {

  private final BadgeIconSupplier myIcon = new BadgeIconSupplier(AllIcons.Vcs.History);

  //todo: store MRU intervals
  private final List<RefreshIntervalAction> myActions = List.of(
    new RefreshIntervalAction(0),
    new RefreshIntervalAction(5),
    new RefreshIntervalAction(10),
    new RefreshIntervalAction(30),
    new RefreshIntervalAction(60),
    new RefreshIntervalAction(300)
  );

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid dataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (dataGrid == null || dataGrid.getDataHookup() instanceof DocumentDataHookUp || dataGrid.getDataHookup() instanceof ImmutableDataHookUp) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setEnabledAndVisible(!hidePageActions(dataGrid, e.getPlace()));
    GridAutoRefresher refresher = GridAutoRefresher.getRefresher(dataGrid);
    e.getPresentation().setIcon(
      refresher != null
      ? refresher.isManuallyPaused() ? myIcon.getWarningIcon() : myIcon.getSuccessIcon()
      : myIcon.getOriginalIcon()
    );
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    DataGrid dataGrid = e == null ? null : e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (dataGrid == null) return AnAction.EMPTY_ARRAY;
    int intervalSec = GridAutoRefresher.getRefreshInterval(dataGrid);
    GridAutoRefresher refresher = GridAutoRefresher.getRefresher(dataGrid);
    List<AnAction> res = new ArrayList<>(myActions.size() + 4);
    if (refresher != null) {
      res.add(new PauseRefresherAction());
    }
    res.addAll(getIntervalActions(intervalSec));
    res.add(Separator.getInstance());
    res.add(new CustomIntervalAction());
    return res.toArray(EMPTY_ARRAY);
  }

  private @NotNull List<RefreshIntervalAction> getIntervalActions(int intervalSec) {
    if (ContainerUtil.find(myActions, a -> a.myIntervalSec == intervalSec) != null) {
      return myActions;
    }
    List<RefreshIntervalAction> actions = new ArrayList<>(myActions.size() + 1);
    actions.addAll(myActions);
    actions.add(new RefreshIntervalAction(intervalSec));
    actions.sort(Comparator.comparing(a -> a.myIntervalSec));
    return actions;
  }

  private static class CustomIntervalAction extends DumbAwareAction implements GridAction {
    private CustomIntervalAction() {
      super(DataGridBundle.message("action.custom.text"));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(e.getData(DatabaseDataKeys.DATA_GRID_KEY) != null);
    }
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      DataGrid dataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
      if (dataGrid == null) {
        return;
      }
      int interval = GridAutoRefresher.getRefreshInterval(dataGrid);
      String res = Messages.showInputDialog(
        e.getProject(), DataGridBundle.message("dialog.message.update.interval.in.seconds"),
        DataGridBundle.message("dialog.title.custom.update.interval"),
        null, Integer.toString(interval), new InputValidator() {
          @Override
          public boolean checkInput(String inputString) {
            return StringUtil.parseInt(inputString, -1) >= 0;
          }

          @Override
          public boolean canClose(String inputString) {
            return StringUtil.parseInt(inputString, -1) >= 0;
          }
        });
      if (res != null) {
        GridAutoRefresher.setRefreshInterval(dataGrid, StringUtil.parseInt(res, 0));
      }
    }
  }
  private static class RefreshIntervalAction extends ToggleAction implements DumbAware, GridAction {
    private final int myIntervalSec;

    private RefreshIntervalAction(int intervalSec) {
      myIntervalSec = intervalSec;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabledAndVisible(e.getData(DatabaseDataKeys.DATA_GRID_KEY) != null);
      e.getPresentation().setText(myIntervalSec == 0 ? DataGridBundle.message("action.disabled.text")
                                                     : Formats.formatDuration(Duration.ofSeconds(myIntervalSec)));
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      DataGrid dataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
      if (dataGrid == null) {
        return false;
      }
      return GridAutoRefresher.getRefreshInterval(dataGrid) == myIntervalSec;
    }


    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      DataGrid dataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
      if (dataGrid == null) {
        return;
      }
      GridAutoRefresher.setRefreshInterval(dataGrid, state ? myIntervalSec : 0);
    }
  }

  private static class PauseRefresherAction extends ToggleAction implements DumbAware, GridAction {
    private PauseRefresherAction() {
      super(DataGridBundle.message("action.pause.text"));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      GridAutoRefresher refresher = GridAutoRefresher.getRefresher(e.getData(DatabaseDataKeys.DATA_GRID_KEY));
      e.getPresentation().setEnabledAndVisible(refresher != null);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      GridAutoRefresher refresher = GridAutoRefresher.getRefresher(e.getData(DatabaseDataKeys.DATA_GRID_KEY));
      return refresher != null && refresher.isManuallyPaused();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      GridAutoRefresher refresher = GridAutoRefresher.getRefresher(e.getData(DatabaseDataKeys.DATA_GRID_KEY));
      if (refresher != null) {
        refresher.setManuallyPaused(state);
      }
    }
  }
}
