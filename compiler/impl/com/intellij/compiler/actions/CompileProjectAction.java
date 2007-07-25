package com.intellij.compiler.actions;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryConfiguration;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ProfilingUtil;

public class CompileProjectAction extends CompileActionBase {
  protected void doAction(DataContext dataContext, final Project project) {
    ProfilingUtil.operationStarted("make");

    CompilerManager.getInstance(project).rebuild(new CompileStatusNotification() {
      public void finished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
        if (!aborted && LocalHistoryConfiguration.getInstance().ADD_LABEL_ON_PROJECT_COMPILATION) {
          String text = getTemplatePresentation().getText();
          LocalHistory.putSystemLabel(project, errors == 0
                                         ? CompilerBundle.message("rebuild.lvcs.label.no.errors", text)
                                         : CompilerBundle.message("rebuild.lvcs.label.with.errors", text));
        }
      }
    });
  }

  public void update(AnActionEvent event) {
    super.update(event);
    Presentation presentation = event.getPresentation();
    if (!presentation.isEnabled()) {
      return;
    }
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    presentation.setEnabled(project != null);
  }
}