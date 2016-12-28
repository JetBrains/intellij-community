/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.dashboard;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author konstantin.aleev
 */
public class RuntimeDashboardManagerImpl implements RuntimeDashboardManager {

  @NotNull private final ContentManager myContentManager;

  public RuntimeDashboardManagerImpl(@NotNull final Project project) {
    ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    ContentUI contentUI = new PanelContentUI();
    myContentManager = contentFactory.createContentManager(contentUI, false, project);

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    ToolWindow toolWindow = toolWindowManager.registerToolWindow(getToolWindowId(), false, ToolWindowAnchor.BOTTOM,
                                                                 project, true);
    toolWindow.setIcon(getToolWindowIcon());
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      RuntimeDashboardContent dashboardContent = new RuntimeDashboardContent(project, myContentManager);
      Content content = contentFactory.createContent(dashboardContent, null, false);
      Disposer.register(content, dashboardContent);
      toolWindow.getContentManager().addContent(content);
    }

    if (!Registry.is("ide.runtime.dashboard")) {
      toolWindow.setAvailable(false, null);
    }

    // TODO [konstantin.aleev] control tool window availability and visibility.
  }

  @Override
  public ContentManager getDashboardContentManager() {
    return myContentManager;
  }

  @Override
  public String getToolWindowId() {
    return ToolWindowId.RUNTIME_DASHBOARD;
  }

  @Override
  public Icon getToolWindowIcon() {
    return AllIcons.Toolwindows.ToolWindowRun; // TODO [konstantin.aleev] provide new icon
  }
}
