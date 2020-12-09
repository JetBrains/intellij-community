// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.util.RunOnceUtil;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.IdeUICustomization;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTree;

public final class ProjectViewToolWindowFactory implements ToolWindowFactory, DumbAware {
  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    ((ProjectViewImpl)ProjectView.getInstance(project)).setupImpl(toolWindow);
  }

  @Override
  public void init(@NotNull ToolWindow window) {
    window.setIcon(IconLoader.getIcon(ApplicationInfoEx.getInstanceEx().getToolWindowIconUrl(), ProjectViewToolWindowFactory.class));
    window.setStripeTitle(IdeUICustomization.getInstance().getProjectViewTitle());
    if (window instanceof ToolWindowEx && Registry.is("ide.open.project.view.on.startup")) {
      Project project = ((ToolWindowEx)window).getProject();
      if (Boolean.TRUE.equals(project.getUserData(FileEditorManagerImpl.NOTHING_WAS_OPENED_ON_START))) {
        RunOnceUtil.runOnceForProject(project, "OpenProjectViewOnStart", () -> {
          ToolWindowManager manager = ToolWindowManager.getInstance(project);
          manager.invokeLater(() -> {
            if (null == manager.getActiveToolWindowId()) {
              window.activate(() -> {
                AbstractProjectViewPane pane = ProjectView.getInstance(project).getCurrentProjectViewPane();
                JTree tree = pane == null ? null : pane.getTree();
                if (tree != null) TreeUtil.promiseSelectFirst(tree).onSuccess(tree::expandPath);
              });
            }
          });
        });
      }
    }
  }
}
