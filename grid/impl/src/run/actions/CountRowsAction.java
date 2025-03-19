package com.intellij.database.run.actions;

import com.intellij.concurrency.AsyncFutureFactory;
import com.intellij.concurrency.AsyncFutureResult;
import com.intellij.database.DataGridBundle;
import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.connection.throwable.info.ErrorInfo;
import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.DataGridRequestPlace;
import com.intellij.database.run.ui.FloatingPagingManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.openapi.roots.ui.configuration.actions.AlignedIconWithTextAction;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

import static com.intellij.database.datagrid.GridUtil.hidePageActions;
import static com.intellij.database.run.actions.ChangePageSizeActionGroup.format;

public class CountRowsAction extends IconWithTextAction implements CustomComponentAction, GridAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (grid != null) {
      countRows(grid);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (grid == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    if (FloatingPagingManager.adjustAction(e) == FloatingPagingManager.AdjustmentResult.HIDDEN) {
      return;
    }

    MyActionState state = getActionState(grid);
    if (hidePageActions(grid, e.getPlace())) {
      e.getPresentation().setVisible(false);
    }
    else {
      e.getPresentation().setVisible(true);
      updatePresentation(state, e.getPresentation());
    }

    super.update(e);
  }

  @Override
  public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    JComponent c = new ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      @Override
      public Color getInactiveTextColor() {
        return getForeground();
      }

      @Override
      public Insets getInsets() {
        return new JBInsets(0, 0, 0, 0);
      }
    };
    return AlignedIconWithTextAction.align(c);
  }

  public static AsyncFutureResult<Long> countRows(@NotNull GridDataHookUp<GridRow, GridColumn> hookUp) {
    return countRows(hookUp, null);
  }

  public static AsyncFutureResult<Long> countRows(@NotNull DataGrid grid) {
    return countRows(grid.getDataHookup(), grid);
  }

  private static AsyncFutureResult<Long> countRows(final @NotNull GridDataHookUp<GridRow, GridColumn> hookUp, @Nullable DataGrid grid) {
    final AsyncFutureResult<Long> result = AsyncFutureFactory.getInstance().createAsyncFutureResult();
    final GridRequestSource requestSource = new GridRequestSource(grid == null ? null : new DataGridRequestPlace(grid));
    final Disposable disposable = Disposer.newDisposable();

    Disposer.register(ObjectUtils.notNull(grid, hookUp.getProject()), disposable);

    hookUp.addRequestListener(new GridDataHookUp.RequestListener<>() {
      @Override
      public void error(@NotNull GridRequestSource source, @NotNull ErrorInfo errorInfo) {
      }

      @Override
      public void updateCountReceived(@NotNull GridRequestSource source, int updateCount) {
        throw new AssertionError("Row count query should not modify data");
      }

      @Override
      public void requestFinished(@NotNull GridRequestSource source, boolean success) {
        if (source != requestSource) return;

        if (success) {
          long rowCount = hookUp.getPageModel().getTotalRowCount();
          result.set(rowCount);
        }
        else {
          result.setException(new Exception("Row count update failed"));
        }

        Disposer.dispose(disposable);
      }
    }, disposable);
    hookUp.getLoader().updateTotalRowCount(requestSource);

    return result;
  }

  private static void updatePresentation(MyActionState state, Presentation presentation) {
    MyActionState oldState = getActionState(presentation);
    if (oldState.equals(state)) return;

    presentation.setText(state.text);
    presentation.setDescription(state.description);
    presentation.setEnabled(state.enabled);
    presentation.setVisible(state.visible);
  }

  private static @NotNull MyActionState getActionState(@NotNull Presentation presentation) {
    String text = presentation.getText();
    String description = presentation.getDescription();
    boolean loading = presentation.isEnabled();
    boolean visible = presentation.isVisible();

    return new MyActionState(text, description, loading, visible);
  }

  private static @NotNull MyActionState getActionState(@NotNull DataGrid grid) {
    GridPagingModel<GridRow, GridColumn> pageModel = grid.getDataHookup().getPageModel();

    int pageEndIdx = pageModel.getPageEnd();
    long totalRowCount = pageModel.getTotalRowCount();
    boolean totalRowCountIsPrecise = pageModel.isTotalRowCountPrecise();
    boolean canUpdateTotalRowCount = pageModel.isTotalRowCountUpdateable();

    boolean rowsWereDeleted = totalRowCount < pageEndIdx;
    String text = DataGridBundle.message("count.rows.action.of.text", format(totalRowCount)) + (totalRowCountIsPrecise ? "" : "+");

    boolean querying = grid.getDataHookup().getBusyCount() > 0;
    boolean isSinglePage = pageModel.isFirstPage() && pageModel.isLastPage() && !rowsWereDeleted;
    boolean visible = !isSinglePage;
    boolean enabled = canUpdateTotalRowCount && !querying && grid.isReady();

    String description = DataGridBundle.message("action.CountRows.description");
    if (!enabled) {
      description = canUpdateTotalRowCount && querying ? DataGridBundle.message("action.CountRows.querying") : "";
    }

    return new MyActionState(text, description, enabled, visible);
  }

  private static final class MyActionState {
    final @NlsActions.ActionText String text;
    final @NlsActions.ActionDescription String description;
    final @NlsContexts.Tooltip boolean enabled;
    final boolean visible;

    private MyActionState(@NlsActions.ActionText String text,
                          @NlsActions.ActionDescription String description,
                          boolean enabled,
                          boolean visible) {
      this.text = text;
      this.description = description;
      this.enabled = enabled;
      this.visible = visible;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MyActionState state = (MyActionState)o;
      return enabled == state.enabled &&
             visible == state.visible &&
             Objects.equals(text, state.text) &&
             Objects.equals(description, state.description);
    }

    @Override
    public int hashCode() {
      return Objects.hash(text, description, enabled, visible);
    }
  }
}
