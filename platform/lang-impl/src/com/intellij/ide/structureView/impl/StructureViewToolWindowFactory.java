package com.intellij.ide.structureView.impl;

import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.project.Project;
import com.intellij.ide.structureView.StructureViewFactory;

/**
 * @author yole
 */
public class StructureViewToolWindowFactory implements ToolWindowFactory {
  public void createToolWindowContent(Project project, ToolWindow toolWindow) {
    StructureViewFactoryImpl factory = (StructureViewFactoryImpl)StructureViewFactory.getInstance(project);
    factory.initToolWindow(toolWindow);
  }
}
