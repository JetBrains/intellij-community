/**
 * class ViewerAction
 * created Aug 27, 2001
 * @author Jeka
 */
package com.intellij.internal.psiView;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

public class ViewerAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    PsiViewerDialog dialog = new PsiViewerDialog(project,false);
    dialog.show();
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    e.getPresentation().setEnabled(project != null);
  }
}
