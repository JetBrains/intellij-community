// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.DesktopLayout;
import org.jetbrains.annotations.NotNull;

public class HideAllToolWindowsAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
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

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = event.getProject();
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
