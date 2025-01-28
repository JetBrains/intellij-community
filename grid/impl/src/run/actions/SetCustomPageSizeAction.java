package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.CustomPageSizeForm;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import static com.intellij.database.run.actions.ChangePageSizeAction.setPageSizeAndReload;

public final class SetCustomPageSizeAction extends DumbAwareAction {
  public SetCustomPageSizeAction() {
    super(ApplicationBundle.messagePointer("custom.option"), ApplicationBundle.messagePointer("custom.option.description"), (Icon)null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    e.getPresentation().setEnabledAndVisible(grid != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (grid == null) return;
    GridPagingModel<GridRow, GridColumn> pageModel = grid.getDataHookup().getPageModel();

    new SetPageSizeDialogWrapper(getEventProject(e)) {
      @Override
      protected int getPageSize() {
        boolean unlimited = GridUtilCore.isPageSizeUnlimited(pageModel.getPageSize());
        return unlimited ? GridUtilCore.getPageSize(GridUtil.getSettings(grid)) : pageModel.getPageSize();
      }

      @Override
      protected boolean isLimitPageSize() {
        return !GridUtilCore.isPageSizeUnlimited(pageModel.getPageSize());
      }

      @Override
      protected void doOKAction() {
        super.doOKAction();
        setPageSizeAndReload(myForm.getPageSize(), grid);
      }
    }.show();
  }

  public abstract static class SetPageSizeDialogWrapper extends DialogWrapper {
    protected final CustomPageSizeForm myForm = new CustomPageSizeForm();

    public SetPageSizeDialogWrapper(@Nullable Project project) {
      super(project);

      setTitle(DataGridBundle.message("dialog.title.change.page.size"));
      initListeners();

      init();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
      return myForm.getResultPageSizeTextField();
    }

    private void initListeners() {
      myForm.getResultPageSizeTextField().getDocument().addDocumentListener(new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent e) {
          updateOk();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
          updateOk();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
          updateOk();
        }
      });
    }

    private void updateOk() {
      getOKAction().setEnabled(isOKActionEnabled());
    }

    @Override
    public boolean isOKActionEnabled() {
      try {
        myForm.getResultPageSizeTextField().validateContent();
        return true;
      }
      catch (ConfigurationException ignored) {
        return false;
      }
    }

    protected abstract int getPageSize();

    protected abstract boolean isLimitPageSize();

    @Override
    protected @NotNull JComponent createCenterPanel() {
      myForm.reset(isLimitPageSize(), getPageSize());
      return myForm.getPanel();
    }
  }
}
