/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;

public abstract class BaseToolWindowToggleAction extends ToggleAction implements DumbAware {

  @Override
  public final boolean isSelected(AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null || project.isDisposed()) {
      return false;
    }
    ToolWindowManager mgr=ToolWindowManager.getInstance(project);
    String id=mgr.getActiveToolWindowId();
    if(id==null){
      return false;
    }
    return isSelected(mgr.getToolWindow(id));
  }

  protected abstract boolean isSelected(ToolWindow window);

  @Override
  public final void setSelected(AnActionEvent e, boolean state) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      return;
    }
    String id=ToolWindowManager.getInstance(project).getActiveToolWindowId();
    if(id==null){
      return;
    }

    ToolWindowManagerEx mgr=ToolWindowManagerEx.getInstanceEx(project);
    ToolWindowEx toolWindow=(ToolWindowEx)mgr.getToolWindow(id);

    setSelected(toolWindow, state);
  }

  protected abstract void setSelected(ToolWindow window, boolean state);

  @Override
  public final void update(AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    ToolWindowManager mgr=ToolWindowManager.getInstance(project);
    String id=mgr.getActiveToolWindowId();

    if (id == null) {
      presentation.setEnabled(false);
      return;
    }

    ToolWindow window = mgr.getToolWindow(id);

    if (window == null) {
      presentation.setEnabled(false);
      return;
    }

    update(window, presentation);
  }

  protected abstract void update(ToolWindow window, Presentation presentation);
}
