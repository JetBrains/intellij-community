/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.EmptyIcon;

import javax.swing.*;

/**
 * @author max
 */
public abstract class QuickSwitchSchemeAction extends AnAction {
  protected static final Icon ourCurrentAction = IconLoader.getIcon("/diff/currentLine.png");
  protected static final Icon ourNotCurrentAction = new EmptyIcon(ourCurrentAction.getIconWidth(), ourCurrentAction.getIconHeight());

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    DefaultActionGroup group = new DefaultActionGroup();
    fillActions(project, group);
    showPopup(e, group);
  }

  protected abstract void fillActions(Project project, DefaultActionGroup group);

  private static void showPopup(AnActionEvent e, DefaultActionGroup group) {
    if (group.getChildrenCount() == 0) return;
    final ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(e.getPresentation().getText(),
                              group,
                              e.getDataContext(),
                              JBPopupFactory.ActionSelectionAid.NUMBERING,
                              true);

    popup.showCenteredInCurrentWindow(e.getData(DataKeys.PROJECT));
  }

  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(e.getData(DataKeys.PROJECT) != null && isEnabled());
  }

  protected abstract boolean isEnabled();
}
