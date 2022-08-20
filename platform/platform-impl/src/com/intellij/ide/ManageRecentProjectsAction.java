// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectFilteringTree;
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectPanelComponentFactory;
import com.intellij.ui.IdeUICustomization;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
final class ManageRecentProjectsAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Disposable disposable = Disposer.newDisposable();
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);

    RecentProjectFilteringTree recentProjectFilteringTree = RecentProjectPanelComponentFactory.createComponent(disposable);
    Tree recentProjectTree = recentProjectFilteringTree.getTree();
    SearchTextField searchTextField = recentProjectFilteringTree.installSearchField();
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(recentProjectTree, true);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setOpaque(false);

    JPanel panel = JBUI.Panels.simplePanel();
    panel.add(searchTextField, BorderLayout.NORTH);
    panel.add(scrollPane, BorderLayout.CENTER);

    PopupUtil.applyNewUIBackground(panel);
    JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, searchTextField)
      .setTitle(IdeUICustomization.getInstance().projectMessage("popup.title.recent.projects"))
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
