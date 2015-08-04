/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowContentUiType;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ShowContentAction extends AnAction implements DumbAware {
  private ToolWindow myWindow;

  @SuppressWarnings({"UnusedDeclaration"})
  public ShowContentAction() {
  }

  public ShowContentAction(ToolWindow window, JComponent c) {
    myWindow = window;
    AnAction original = ActionManager.getInstance().getAction("ShowContent");
    new ShadowAction(this, original, c);
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

    Project project = CommonDataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) return null;

    ToolWindowManager manager = ToolWindowManager.getInstance(project);

    final ToolWindow window = manager.getToolWindow(manager.getActiveToolWindowId());
    if (window == null) return null;

    final Component context = PlatformDataKeys.CONTEXT_COMPONENT.getData(event.getDataContext());
    if (context == null) return null;

    return SwingUtilities.isDescendingFrom(window.getComponent(), context) ? window : null;
  }
}
