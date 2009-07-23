package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;

/**
 * @author yole
 */
public class ProjectViewToolWindowFactory implements ToolWindowFactory {
  public void createToolWindowContent(Project project, ToolWindow toolWindow) {
    ((ProjectViewImpl) ProjectView.getInstance(project)).setupImpl(toolWindow);
  }
}
