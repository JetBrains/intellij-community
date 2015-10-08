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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.DesktopLayout;

public class HideAllToolWindowsAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      return;
    }

    performAction(project);
  }

  public static void performAction(final Project project) {
    ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(project);

    DesktopLayout layout = new DesktopLayout();
    layout.copyFrom(toolWindowManager.getLayout());

    // to clear windows stack
    toolWindowManager.clearSideStack();
    //toolWindowManager.activateEditorComponent();


    String[] ids = toolWindowManager.getToolWindowIds();
    boolean hasVisible = false;
    for (String id : ids) {
      if (HideToolWindowAction.shouldBeHiddenByShortCut(toolWindowManager, id)) {
        toolWindowManager.getToolWindow(id).hide(null);
        hasVisible = true;
      }
    }

    if (hasVisible) {
      toolWindowManager.setLayoutToRestoreLater(layout);
      toolWindowManager.activateEditorComponent();
    }
    else {
      final DesktopLayout restoredLayout = toolWindowManager.getLayoutToRestoreLater();
      if (restoredLayout != null) {
        toolWindowManager.setLayoutToRestoreLater(null);
        toolWindowManager.setLayout(restoredLayout);
      }
    }
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = CommonDataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(project);
    String[] ids = toolWindowManager.getToolWindowIds();
    for (String id : ids) {
      if (HideToolWindowAction.shouldBeHiddenByShortCut(toolWindowManager, id)) {
        presentation.setEnabled(true);
        presentation.setText(IdeBundle.message("action.hide.all.windows"), true);
        return;
      }
    }

    final DesktopLayout layout = toolWindowManager.getLayoutToRestoreLater();
    if (layout != null) {
      presentation.setEnabled(true);
      presentation.setText(IdeBundle.message("action.restore.windows"));
      return;
    }

    presentation.setEnabled(false);
  }
}
