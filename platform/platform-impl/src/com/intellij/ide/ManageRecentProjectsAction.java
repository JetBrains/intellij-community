// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.impl.welcomeScreen.NewRecentProjectPanel;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * @author Konstantin Bulenkov
 */
final class ManageRecentProjectsAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Disposable disposable = Disposer.newDisposable();
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    NewRecentProjectPanel panel = new NewRecentProjectPanel(disposable) {
      @Override
      protected JBList<AnAction> createList(AnAction[] recentProjectActions, Dimension size) {
        return super.createList(RecentProjectsGroup.removeCurrentProject(project, Arrays.asList(recentProjectActions)), size);
      }
    };
    JList<?> list = UIUtil.findComponentOfType(panel, JList.class);
    PopupUtil.applyNewUIBackground(panel);
    SearchTextField searchTextField = UIUtil.findComponentOfType(panel, SearchTextField.class);
    PopupUtil.applyNewUIBackground(searchTextField);
    JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, list)
      .setTitle(IdeBundle.message("popup.title.recent.projects"))
      .setFocusable(true)
      .setRequestFocus(true)
      .setMayBeParent(true)
      .setDimensionServiceKey(null, "manage.recent.projects.popup", false)
      .setMovable(true)
      .setResizable(true)
      .setNormalWindowLevel(true)
      .createPopup();
    Disposer.register(popup, disposable);

    popup.showCenteredInCurrentWindow(project);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    boolean enable = project != null && !RecentProjectListActionProvider.getInstance().getActions(false).isEmpty();
    e.getPresentation().setEnabledAndVisible(enable);
  }
}
