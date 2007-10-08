/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.samples;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ui.UIUtil;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

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

    myContentPanel.setBackground(UIUtil.getTreeTextBackground());
    myContentPanel.add(new JLabel("Hello World!", JLabel.CENTER), BorderLayout.CENTER);

    myToolWindow = toolWindowManager.registerToolWindow(TOOL_WINDOW_ID, false, ToolWindowAnchor.LEFT);
    ContentFactory contentFactory = PeerFactory.getInstance().getContentFactory();
    Content content = contentFactory.createContent(myContentPanel, "SimpleWindow", false);
    myToolWindow.getContentManager().addContent(content);
  }

  private void unregisterToolWindow() {
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    toolWindowManager.unregisterToolWindow(TOOL_WINDOW_ID);
  }
}
