package com.intellij.ide.actions;

import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;

/**
 *
 */
public class CloseProjectAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    assert project != null;

    ProjectUtil.closeProject(project);
    RecentProjectsManagerBase.getInstance().updateLastProjectPath();
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    presentation.setEnabled(project != null);
  }
}
