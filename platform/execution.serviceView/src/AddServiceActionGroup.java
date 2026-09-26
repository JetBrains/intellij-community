// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView;

import com.intellij.execution.services.ServiceViewAddActionContributor;
import com.intellij.execution.services.ServiceViewManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.wm.ToolWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.platform.execution.serviceView.ServiceViewActionProvider.getSelectedView;

final class AddServiceActionGroup extends DefaultActionGroup implements DumbAware {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(getSelectedView(e) != null);
    e.getPresentation().setPerformGroup(false);
  }

  @Override
  public @Unmodifiable @NotNull List<? extends @NotNull AnAction> postProcessVisibleChildren(
    @NotNull AnActionEvent e,
    @NotNull List<? extends @NotNull AnAction> visibleChildren) {
    ToolWindow toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW);
    if (toolWindow == null) {
      return super.postProcessVisibleChildren(e, visibleChildren);
    }
    Project project = e.getProject();
    if (project == null) {
      return super.postProcessVisibleChildren(e, visibleChildren);
    }
    String currentToolWindowId = toolWindow.getId();
    ServiceViewManager serviceViewManager = ServiceViewManager.getInstance(project);
    Map<Class<?>, String> idCache = new HashMap<>();
    List<AnAction> filtered = new ArrayList<>(visibleChildren.size());
    for (AnAction child : visibleChildren) {
      if (child instanceof ServiceViewAddActionContributor contributor) {
        Class<?> contributorClass = contributor.getContributorClass();
        String expectedId = idCache.computeIfAbsent(contributorClass, serviceViewManager::getToolWindowId);
        if (!currentToolWindowId.equals(expectedId)) {
          continue;
        }
      }
      filtered.add(child);
    }
    return super.postProcessVisibleChildren(e, filtered);
  }
}
