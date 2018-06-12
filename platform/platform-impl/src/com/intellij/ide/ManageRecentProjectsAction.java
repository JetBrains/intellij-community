// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.actions.RecentProjectsGroup;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.impl.welcomeScreen.NewRecentProjectPanel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class ManageRecentProjectsAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Disposable disposable = Disposer.newDisposable();
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    NewRecentProjectPanel panel = new NewRecentProjectPanel(disposable) {
      @Override
      protected JBList createList(AnAction[] recentProjectActions, Dimension size) {
        return super.createList(RecentProjectsGroup.removeCurrentProject(project, recentProjectActions), size);
      }
    };
    JList list = UIUtil.findComponentOfType(panel, JList.class);
    JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, list)
      .setTitle("Recent Projects")
      .setFocusable(true)
      .setRequestFocus(true)
      .setMayBeParent(true)
      .setMovable(true)
      .createPopup();
    Disposer.register(popup, disposable);

    popup.showCenteredInCurrentWindow(project);
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    boolean enable = false;
    if (project != null) {
      enable = RecentProjectsManager.getInstance().getRecentProjectsActions(false).length > 0;
    }

    e.getPresentation().setEnabledAndVisible(enable);
  }
}
