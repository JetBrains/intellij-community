// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard.actions;

import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.services.ServiceViewActionUtils;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Internal
public final class RunDashboardActionUtils {
  private RunDashboardActionUtils() {
  }

  public static @NotNull List<RunDashboardRunConfigurationNode> getTargets(@NotNull AnActionEvent e) {
    return ServiceViewActionUtils.getTargets(e, RunDashboardRunConfigurationNode.class);
  }

  public static @Nullable RunDashboardRunConfigurationNode getTarget(@NotNull AnActionEvent e) {
    return ServiceViewActionUtils.getTarget(e, RunDashboardRunConfigurationNode.class);
  }

  public static @NotNull JBIterable<RunDashboardRunConfigurationNode> getLeafTargets(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return JBIterable.empty();

    JBIterable<Object> roots = JBIterable.of(e.getData(PlatformCoreDataKeys.SELECTED_ITEMS));
    Set<RunDashboardRunConfigurationNode> result = new LinkedHashSet<>();
    if (!getLeaves(project, e, roots.toList(), Collections.emptyList(), result)) return JBIterable.empty();

    return JBIterable.from(result);
  }

  private static boolean getLeaves(Project project, AnActionEvent e, List<Object> items, List<Object> valueSubPath,
                                   Set<? super RunDashboardRunConfigurationNode> result) {
    for (Object item : items) {
      if (item instanceof RunDashboardGroupNode groupNode) {
        List<Object> itemSubPath = new ArrayList<>(valueSubPath);
        itemSubPath.add(item);
        List<Object> children = groupNode.getChildren(project, e);
        if (!getLeaves(project, e, children, itemSubPath, result)) {
          return false;
        }
      }
      else if (item instanceof RunDashboardRunConfigurationNode) {
        result.add((RunDashboardRunConfigurationNode)item);
      }
      else if (item instanceof AbstractTreeNode) {
        AbstractTreeNode<?> parent = ((AbstractTreeNode<?>)item).getParent();
        if (parent instanceof RunDashboardRunConfigurationNode) {
          result.add((RunDashboardRunConfigurationNode)parent);
        }
        else {
          return false;
        }
      }
      else {
        return false;
      }
    }
    return true;
  }
}
