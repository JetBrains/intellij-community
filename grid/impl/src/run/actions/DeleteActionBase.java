package com.intellij.database.run.actions;

import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.util.DataGridUIUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.actions.DeleteAction;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DeleteActionBase extends DeleteAction implements DeleteProvider, GridAction {
  protected DeleteActionBase() {
    super(null, null, AllIcons.General.Remove);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);

    DataGrid grid = event.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (grid == null) {
      event.getPresentation().setText(getTemplatePresentation().getText());
    }
    else {
      int itemsCount = itemsCount(grid);
      String text = itemsCount == 1 ? text(grid).simpleDelete : text(grid).simpleDeletePlural;
      event.getPresentation().setText(text);
    }

    event.getPresentation().setEnabled(grid != null && isEnabled(grid));
    event.getPresentation().setVisible(isVisible(grid));
  }

  @Override
  protected @NotNull DeleteProvider getDeleteProvider(DataContext dataContext) {
    return this;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    DataGrid grid = DatabaseDataKeys.DATA_GRID_KEY.getData(dataContext);
    var focusedComponent = dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    return grid != null && isVisible(grid) &&
           (focusedComponent == null || DataGridUIUtil.isInsideGrid(grid, focusedComponent)) && itemsCount(grid) > 0;
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
    DataGrid grid = DatabaseDataKeys.DATA_GRID_KEY.getData(dataContext);
    if (grid == null) return;
    ModelIndex<GridColumn> column = grid.getContextColumn();

    if (!ApplicationManager.getApplication().isUnitTestMode() && grid.getDataSupport().isSubmitImmediately()) {
      int itemsCount = itemsCount(grid);
      ActionText text = text(grid);
      String title = itemsCount == 1 ? String.format(text.dialogTitlePattern, getItemName(grid)) : text.dialogTitlePlural;
      String message = itemsCount == 1 ? String.format(text.confirmationPattern, getItemName(grid)) : String.format(text.confirmationPluralPattern, itemsCount);

      if (!MessageDialogBuilder.okCancel(title, message).ask(grid.getPanel().getComponent())) {
        return;
      }
    }

    doDelete(grid, column);
  }

  protected abstract @NlsSafe @NotNull String getItemName(@NotNull DataGrid grid);

  protected abstract boolean isEnabled(@NotNull DataGrid grid);

  protected abstract void doDelete(@NotNull DataGrid grid, @NotNull ModelIndex<GridColumn> contextColumn);

  protected abstract int itemsCount(@NotNull DataGrid grid);

  protected abstract @NotNull ActionText text(@NotNull DataGrid grid);

  protected abstract boolean isVisible(@Nullable DataGrid grid);

  protected static class ActionText {
    final @Nls(capitalization = Nls.Capitalization.Title) String simpleDelete;
    final @Nls(capitalization = Nls.Capitalization.Title) String simpleDeletePlural;
    final @Nls(capitalization = Nls.Capitalization.Title) String dialogTitlePattern;
    final @Nls(capitalization = Nls.Capitalization.Title) String dialogTitlePlural;
    final @Nls(capitalization = Nls.Capitalization.Sentence) String confirmationPattern;
    final @Nls(capitalization = Nls.Capitalization.Sentence) String confirmationPluralPattern;

    public ActionText(@Nls(capitalization = Nls.Capitalization.Title) String simpleDelete,
                      @Nls(capitalization = Nls.Capitalization.Title) String simpleDeletePlural,
                      @Nls(capitalization = Nls.Capitalization.Title) String dialogTitlePattern,
                      @Nls(capitalization = Nls.Capitalization.Title) String dialogTitlePlural,
                      @Nls(capitalization = Nls.Capitalization.Sentence) String confirmationPattern,
                      @Nls(capitalization = Nls.Capitalization.Sentence) String confirmationPluralPattern) {
      this.simpleDelete = simpleDelete;
      this.simpleDeletePlural = simpleDeletePlural;
      this.dialogTitlePattern = dialogTitlePattern;
      this.dialogTitlePlural = dialogTitlePlural;
      this.confirmationPattern = confirmationPattern;
      this.confirmationPluralPattern = confirmationPluralPattern;
    }
  }
}
