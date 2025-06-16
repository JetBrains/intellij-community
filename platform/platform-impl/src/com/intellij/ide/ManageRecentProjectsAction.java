// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager;
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.ProjectCollectors;
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectFilteringTree;
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectPanelComponentFactory;
import com.intellij.ui.IdeUICustomization;
import com.intellij.ui.ListFocusTraversalPolicy;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
final class ManageRecentProjectsAction extends DumbAwareAction {
  private static final int DEFAULT_POPUP_HEIGHT = 485;
  private static final int DEFAULT_POPUP_WIDTH = 300;

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    Disposable disposable = Disposer.newDisposable();

    RecentProjectFilteringTree recentProjectFilteringTree = RecentProjectPanelComponentFactory.createComponent(
      disposable, List.of(ProjectCollectors.createRecentProjectsWithoutCurrentCollector(project)),
      WelcomeScreenUIManager.getProjectsBackground()
    );
    Tree recentProjectTree = recentProjectFilteringTree.getTree();
    TreeUtil.selectFirstNode(recentProjectTree);
    SearchTextField searchTextField = recentProjectFilteringTree.installSearchField();
    searchTextField.setBorder(JBUI.Borders.customLineBottom(WelcomeScreenUIManager.getSeparatorColor()));
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(recentProjectTree, true);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    ComponentContainer componentContainer = new ComponentContainer() {
      private final JBTextField textField = searchTextField.getTextEditor();

      @Override
      public @NotNull JComponent getComponent() {
        JPanel panel = new JPanel(null);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(WelcomeScreenUIManager.getProjectsBackground());
        panel.setFocusTraversalPolicy(new ListFocusTraversalPolicy(
          Arrays.asList(textField, recentProjectTree)
        ));
        panel.setFocusTraversalPolicyProvider(true);
        panel.setFocusCycleRoot(true);

        panel.add(searchTextField);
        panel.add(scrollPane);

        panel.setPreferredSize(JBUI.size(DEFAULT_POPUP_WIDTH, DEFAULT_POPUP_HEIGHT));

        return panel;
      }

      @Override
      public JComponent getPreferredFocusableComponent() {
        return textField;
      }

      @Override
      public void dispose() { }
    };

    PopupUtil.applyNewUIBackground(componentContainer.getComponent());
    JBPopup popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(componentContainer.getComponent(), componentContainer.getPreferredFocusableComponent())
      .setTitle(IdeUICustomization.getInstance().projectMessage("popup.title.recent.projects"))
      .setFocusable(true)
      .setRequestFocus(true)
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

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
