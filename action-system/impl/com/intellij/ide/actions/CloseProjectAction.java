
package com.intellij.ide.actions;

import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;

/**
 *
 */
public class CloseProjectAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = DataKeys.PROJECT.getData(e.getDataContext());
    assert project != null;

    ProjectUtil.closeProject(project);
    RecentProjectsManager.getInstance().updateLastProjectPath();
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = DataKeys.PROJECT.getData(event.getDataContext());
    presentation.setEnabled(project != null);
  }
}
