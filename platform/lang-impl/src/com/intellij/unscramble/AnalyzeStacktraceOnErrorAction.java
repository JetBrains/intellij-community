package com.intellij.unscramble;

import com.intellij.diagnostic.IdeErrorsDialog;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;

/**
 * @author spleaner
 */
public class AnalyzeStacktraceOnErrorAction extends AnAction {

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();

    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;

    final String message = IdeErrorsDialog.CURRENT_TRACE_KEY.getData(dataContext);
    if (message != null) {
      final ConsoleView consoleView = AnalyzeStacktraceUtil.addConsole(project, null, "<Stacktrace>");
      AnalyzeStacktraceUtil.printStacktrace(consoleView, message);
    }
  }
}