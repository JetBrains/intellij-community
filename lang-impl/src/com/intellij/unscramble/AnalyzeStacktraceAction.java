package com.intellij.unscramble;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

/**
 * Simple variant of Analyze Stacktrace that doesn't handle unscramblers or multiple-thread
 * thread dumps.
 *
 * @author yole
 */
public class AnalyzeStacktraceAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    AnalyzeStacktraceDialog dialog = new AnalyzeStacktraceDialog(project);
    dialog.show();
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(e.getData(PlatformDataKeys.PROJECT) != null);
  }
}
