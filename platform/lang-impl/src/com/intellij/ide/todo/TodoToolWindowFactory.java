package com.intellij.ide.todo;

import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ServiceManager;

/**
 * @author yole
 */
public class TodoToolWindowFactory implements ToolWindowFactory {
  public void createToolWindowContent(Project project, ToolWindow toolWindow) {
    TodoView todoView = ServiceManager.getService(project, TodoView.class);
    todoView.initToolWindow(toolWindow);
  }
}
