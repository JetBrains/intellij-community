package com.intellij.openapi.samples;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;

import javax.swing.*;
import java.awt.*;

public class SimpleToolWindowPlugin implements ProjectComponent {
  private Project myProject;

  private ToolWindow myToolWindow;
  private JPanel myContentPanel;

  public static final String TOOL_WINDOW_ID = "SimpleToolWindow";

  public SimpleToolWindowPlugin(Project project) {
    myProject = project;
  }

  public void projectOpened() {
    initToolWindow();
  }

  public void projectClosed() {
    unregisterToolWindow();
  }

  public void initComponent() {
    // empty
  }

  public void disposeComponent() {
    // empty
  }

  public String getComponentName() {
    return "SimpleToolWindow.SimpleToolWindowPlugin";
  }

  private void initToolWindow() {
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);

    myContentPanel = new JPanel(new BorderLayout());

    myContentPanel.setBackground(UIManager.getColor("Tree.textBackground"));
    myContentPanel.add(new JLabel("Hello World!", JLabel.CENTER), BorderLayout.CENTER);

    myToolWindow = toolWindowManager.registerToolWindow(TOOL_WINDOW_ID, myContentPanel, ToolWindowAnchor.LEFT);
    myToolWindow.setTitle("SimpleWindow");
  }

  private void unregisterToolWindow() {
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    toolWindowManager.unregisterToolWindow(TOOL_WINDOW_ID);
  }
}
