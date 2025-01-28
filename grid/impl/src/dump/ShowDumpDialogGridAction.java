package com.intellij.database.dump;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridHelper;
import com.intellij.database.dump.ExtractionHelper.ClipboardExtractionHelper;
import com.intellij.database.dump.ExtractionHelper.FileExtractionHelper;
import com.intellij.database.extractors.DataExtractorFactory;
import com.intellij.database.extractors.ExtractionConfig;
import com.intellij.database.run.actions.DumpSource;
import com.intellij.database.run.actions.GridAction;
import com.intellij.database.view.ui.DumpDataDialog;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.intellij.database.DatabaseDataKeys.DATA_GRID_KEY;
import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT;

public class ShowDumpDialogGridAction extends DumbAwareAction implements GridAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid grid = e.getData(DATA_GRID_KEY);
    DumpSource<?> source = grid == null ? null : getDumpSource(grid, e);
    if (source == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(GridHelper.get(grid).isDumpEnabled(source));
    e.getPresentation().setText(DataGridBundle.message("ShowDumpDialogAction.text"));
    e.getPresentation().setText(DataGridBundle.message("ShowDumpDialogAction.DatabaseViewText", DumpSource.getSize(source)), true);
    e.getPresentation().setIcon(getTemplatePresentation().getIcon());
  }

  private static @Nullable DumpSource<?> getDumpSource(@NotNull DataGrid grid, @NotNull AnActionEvent e) {
    return GridHelper.get(grid).createDumpSource(grid, e);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    DataGrid grid = e.getData(DATA_GRID_KEY);
    if (grid == null) return;
    DumpSource<?> source = getDumpSource(grid, e);

    if (project != null && source != null) {
      new DumpDataDialog(project, source, e.getData(CONTEXT_COMPONENT)) {
        @Override
        protected void exportToFile(@NotNull DataExtractorFactory factory, @NotNull File file) {
          export(project, grid, source, factory, new FileExtractionHelper(file), myForm.getExtractorConfig());
        }

        @Override
        protected void exportToClipboard(@NotNull DataExtractorFactory factory) {
          export(project, grid, source, factory, new ClipboardExtractionHelper(), myForm.getExtractorConfig());
        }
      }.show();
    }
  }

  private static void export(@NotNull Project project,
                             @NotNull DataGrid grid,
                             @NotNull DumpSource<?> source,
                             @NotNull DataExtractorFactory factory,
                             @NotNull ExtractionHelper helper,
                             @NotNull ExtractionConfig config) {
    GridHelper.get(grid).createDumpHandler(source, helper, factory, config).performDump(project);
  }
}