// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowContentUiType;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class ShowContentAction extends AnAction implements DumbAware {
  public static final @NonNls String ACTION_ID = "ShowContent";

  private ToolWindow myWindow;

  @SuppressWarnings("UnusedDeclaration")
  public ShowContentAction() {
  }

  /**
   * @deprecated please get this action using {@link ActionManager#getAction(String)} or create your own action.
   */
  @Deprecated(forRemoval = true)
  public ShowContentAction(@NotNull ToolWindow window, JComponent c, @NotNull Disposable parentDisposable) {
    myWindow = window;
    new ShadowAction(this, ACTION_ID, c, parentDisposable);
    ActionUtil.copyFrom(this, ACTION_ID);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    ToolWindow window = myWindow != null ? myWindow : e.getData(PlatformDataKeys.TOOL_WINDOW);
    e.getPresentation().setText(window == null || window.getContentUiType() == ToolWindowContentUiType.TABBED
                                ? ActionsBundle.message("action.ShowContent.text")
                                : ActionsBundle.message("action.ShowContent.views.text"));

    ContentManager contentManager = e.getData(PlatformDataKeys.TOOL_WINDOW_CONTENT_MANAGER);
    e.getPresentation().setEnabledAndVisible(contentManager != null && contentManager.getContentCount() > 1);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ToolWindowContentUi contentUi = e.getData(ToolWindowContentUi.DATA_KEY);
    if (contentUi != null) {
      ToolWindowContentUi.toggleContentPopup(contentUi, contentUi.getContentManager());
    }
  }
}