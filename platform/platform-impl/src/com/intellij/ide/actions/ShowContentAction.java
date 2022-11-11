// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowContentUiType;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class ShowContentAction extends AnAction implements DumbAware {
  @NonNls public static final String ACTION_ID = "ShowContent";

  private ToolWindow myWindow;

  @SuppressWarnings({"UnusedDeclaration"})
  public ShowContentAction() {
  }

  public ShowContentAction(@NotNull ToolWindow window, JComponent c, @NotNull Disposable parentDisposable) {
    myWindow = window;
    new ShadowAction(this, ACTION_ID, c, parentDisposable);
    ActionUtil.copyFrom(this, ACTION_ID);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final ToolWindow window = getWindow(e);
    e.getPresentation().setEnabledAndVisible(window != null && window.getContentManager().getContentCount() > 1);
    e.getPresentation().setText(window == null || window.getContentUiType() == ToolWindowContentUiType.TABBED
                                ? ActionsBundle.message("action.ShowContent.text")
                                : ActionsBundle.message("action.ShowContent.views.text"));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ToolWindow toolWindow = getWindow(e);
    if (toolWindow != null) {
      toolWindow.showContentPopup(e.getInputEvent());
    }
  }

  @Nullable
  private ToolWindow getWindow(@NotNull AnActionEvent event) {
    if (myWindow != null) {
      return myWindow;
    }

    Project project = event.getProject();
    if (project == null) {
      return null;
    }

    Component context = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    if (context == null) {
      return null;
    }

    ToolWindowManager manager = ToolWindowManager.getInstance(project);
    String toolWindowId = manager.getActiveToolWindowId();
    ToolWindow window = toolWindowId == null ? null : manager.getToolWindow(toolWindowId);
    if (window == null) {
      return null;
    }

    return SwingUtilities.isDescendingFrom(window.getComponent(), context) ? window : null;
  }
}