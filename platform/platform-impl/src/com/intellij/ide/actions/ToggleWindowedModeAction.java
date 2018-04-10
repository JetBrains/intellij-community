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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;

public class ToggleWindowedModeAction extends ToggleAction implements DumbAware {

  public boolean isSelected(AnActionEvent event) {
    Project project = event.getProject();
    if (project == null) {
      return false;
    }
    ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
    String id = windowManager.getActiveToolWindowId();
    if (id == null) {
      return false;
    }
    return ToolWindowType.WINDOWED == windowManager.getToolWindow(id).getType();
  }

  public void setSelected(AnActionEvent event, boolean flag) {
    Project project = event.getProject();
    if (project == null) {
      return;
    }
    String id = ToolWindowManager.getInstance(project).getActiveToolWindowId();
    if (id == null) {
      return;
    }
    ToolWindowManagerEx mgr = ToolWindowManagerEx.getInstanceEx(project);
    ToolWindowEx toolWindow = (ToolWindowEx)mgr.getToolWindow(id);
    ToolWindowType type = toolWindow.getType();
    if (ToolWindowType.WINDOWED == type) {
      toolWindow.setType(toolWindow.getInternalType(), null);
    }
    else {
      toolWindow.setType(ToolWindowType.WINDOWED, null);
    }
  }

  public void update(AnActionEvent event) {
    super.update(event);
    Presentation presentation = event.getPresentation();
    Project project = event.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    ToolWindowManager mgr = ToolWindowManager.getInstance(project);
    String id = mgr.getActiveToolWindowId();
    presentation.setEnabled(id != null && mgr.getToolWindow(id).isAvailable());
  }
}
