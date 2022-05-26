// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.util.RunOnceUtil;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.module.GeneralModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Objects;

public final class ProjectViewToolWindowFactory implements ToolWindowFactory, DumbAware {
  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    ((ProjectViewImpl)ProjectView.getInstance(project)).setupImpl(toolWindow);
  }

  @Override
  public @NotNull Icon getIcon() {
    String path = ApplicationInfoEx.getInstanceEx().getToolWindowIconUrl();
    if (path.equals("/toolwindows/toolWindowProject.svg") || path.equals("toolwindows/toolWindowProject.svg")) {
      return AllIcons.Toolwindows.ToolWindowProject;
    }
    else {
      return Objects.requireNonNull(IconLoader.findIcon(path, null, ProjectViewToolWindowFactory.class.getClassLoader(), null, false));
    }
  }

  @Override
  public void init(@NotNull ToolWindow window) {
    if (!(window instanceof ToolWindowEx) || !Registry.is("ide.open.project.view.on.startup", true)) {
      return;
    }

    Project project = window.getProject();
    if (Boolean.TRUE.equals(project.getUserData(FileEditorManagerImpl.NOTHING_WAS_OPENED_ON_START)) &&
        !ProjectUtil.isNotificationSilentMode(project)) {
      RunOnceUtil.runOnceForProject(project, "OpenProjectViewOnStart", () -> {
        ToolWindowManager manager = ToolWindowManager.getInstance(project);
        manager.invokeLater(() -> {
          if (manager.getActiveToolWindowId() == null) {
            window.activate(() -> {
              Module[] modules = ModuleManager.getInstance(project).getModules();
              if (modules.length == 1 && GeneralModuleType.TYPE_ID.equals(modules[0].getModuleTypeName()))
              {
                return;
              }
              AbstractProjectViewPane pane = ProjectView.getInstance(project).getCurrentProjectViewPane();
              JTree tree = pane == null ? null : pane.getTree();
              if (tree != null) {
                TreeUtil.promiseSelectFirst(tree).onSuccess(tree::expandPath);
              }
            });
          }
        });
      });
    }
  }
}
