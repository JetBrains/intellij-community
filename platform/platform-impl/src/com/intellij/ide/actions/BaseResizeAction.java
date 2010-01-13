/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;

public abstract class BaseResizeAction extends AnAction implements DumbAware {

  private ToolWindow myLastWindow;
  private ToolWindowManager myLastManager;
  
  @Override
  public final void update(AnActionEvent e) {
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      setDisabled(e);
      return;
    }

    ToolWindowManager mgr = ToolWindowManager.getInstance(project);

    String active = mgr.getActiveToolWindowId();
    if (active != null) {
      ToolWindow window = mgr.getToolWindow(active);

      if (!window.isAvailable() || !window.isVisible() || window.getType() == ToolWindowType.FLOATING) {
        setDisabled(e);
        return;
      }

      update(window, mgr);
      if (e.getPresentation().isEnabled()) {
        myLastWindow = window;
        myLastManager = mgr;
      } else {
        setDisabled(e);
      }
    } else {
      setDisabled(e);
    }
  }

  private void setDisabled(AnActionEvent e) {
    e.getPresentation().setEnabled(false);
    myLastWindow = null;
    myLastManager = null;
  }

  protected abstract void update(ToolWindow window, ToolWindowManager mgr);

  @Override
  public final void actionPerformed(AnActionEvent e) {
    actionPerformed(e, myLastWindow, myLastManager);
  }

  protected abstract void actionPerformed(AnActionEvent e, ToolWindow wnd, ToolWindowManager mgr);
}
