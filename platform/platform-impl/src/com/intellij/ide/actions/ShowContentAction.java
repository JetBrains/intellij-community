// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowContentUiType;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ShowContentAction extends AnAction implements DumbAware {
  private ToolWindow myWindow;

  @SuppressWarnings({"UnusedDeclaration"})
  public ShowContentAction() {
  }

  public ShowContentAction(ToolWindow window, JComponent c, @NotNull Disposable parentDisposable) {
    myWindow = window;
    AnAction original = ActionManager.getInstance().getAction("ShowContent");
    new ShadowAction(this, original, c, parentDisposable);
    copyFrom(original);
  }

  @Override
  public void update(AnActionEvent e) {
    final ToolWindow window = getWindow(e);
    e.getPresentation().setEnabledAndVisible(window != null && window.getContentManager().getContentCount() > 1);
    e.getPresentation().setText(window == null || window.getContentUiType() == ToolWindowContentUiType.TABBED
                                ? "Show List of Tabs"
                                : "Show List of Views");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    getWindow(e).showContentPopup(e.getInputEvent());
  }

  @Nullable
  private ToolWindow getWindow(AnActionEvent event) {
    if (myWindow != null) return myWindow;

    Project project = event.getProject();
    if (project == null) return null;

    ToolWindowManager manager = ToolWindowManager.getInstance(project);

    final ToolWindow window = manager.getToolWindow(manager.getActiveToolWindowId());
    if (window == null) return null;

    final Component context = PlatformDataKeys.CONTEXT_COMPONENT.getData(event.getDataContext());
    if (context == null) return null;

    return SwingUtilities.isDescendingFrom(window.getComponent(), context) ? window : null;
  }
}
