package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;

/**
 * @author yole
 */
public class NewDummyProjectAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    final ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    Project project = projectManager.newProject("/dummy.ipr", true, false);
    projectManager.openProject(project);
  }

  @Override
  public void update(final AnActionEvent e) {
    e.getPresentation().setVisible(System.getProperty("idea.platform") != null);
  }
}