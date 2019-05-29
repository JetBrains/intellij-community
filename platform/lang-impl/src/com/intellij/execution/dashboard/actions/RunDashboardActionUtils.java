// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard.actions;

import com.intellij.execution.dashboard.RunConfigurationsServiceViewContributor;
import com.intellij.execution.dashboard.RunDashboardGroup;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.dashboard.tree.GroupingNode;
import com.intellij.execution.services.ServiceViewActionUtils;
import com.intellij.execution.services.ServiceViewManager;
import com.intellij.execution.services.ServiceViewManagerImpl;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.containers.TreeTraversal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class RunDashboardActionUtils {
  private RunDashboardActionUtils() {
  }

  @NotNull
  static JBIterable<RunDashboardRunConfigurationNode> getTargets(@NotNull AnActionEvent e) {
    return ServiceViewActionUtils.getTargets(e, RunDashboardRunConfigurationNode.class);
  }

  @Nullable
  static RunDashboardRunConfigurationNode getTarget(@NotNull AnActionEvent e) {
    return ServiceViewActionUtils.getTarget(e, RunDashboardRunConfigurationNode.class);
  }

  @NotNull
  static JBIterable<RunDashboardRunConfigurationNode> getLeafTargets(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return JBIterable.empty();

    JBIterable<Object> roots = JBIterable.of(e.getData(PlatformDataKeys.SELECTED_ITEMS));
    if (Registry.is("ide.service.view")) {
      Set<RunDashboardRunConfigurationNode> result = new LinkedHashSet<>();
      if (!getLeaves(project, roots.toList(), result)) return JBIterable.empty();

      return JBIterable.from(result);
    }
    else {
      JBIterable<Object> leaves = JBTreeTraverser.from(o -> o instanceof GroupingNode ? ((GroupingNode)o).getChildren() : null)
        .withRoots(roots)
        .traverse(TreeTraversal.LEAVES_DFS)
        .map(leaf -> {
          if (leaf instanceof AbstractTreeNode) {
            AbstractTreeNode parent = ((AbstractTreeNode)leaf).getParent();
            return parent instanceof RunDashboardRunConfigurationNode ? parent : leaf;
          }
          return leaf;
        })
        .unique();
      if (leaves.filter(leaf -> !(leaf instanceof RunDashboardRunConfigurationNode)).isNotEmpty()) return JBIterable.empty();

      return leaves.filter(RunDashboardRunConfigurationNode.class);
    }
  }

  private static boolean getLeaves(Project project, List<Object> items, Set<RunDashboardRunConfigurationNode> result) {
    for (Object item : items) {
      if (item instanceof RunConfigurationsServiceViewContributor || item instanceof RunDashboardGroup) {
        List<Object> children = ((ServiceViewManagerImpl)ServiceViewManager.getInstance(project)).getChildrenSafe(item);
        if (!getLeaves(project, children, result)) {
          return false;
        }
      }
      else if (item instanceof RunDashboardRunConfigurationNode) {
        result.add((RunDashboardRunConfigurationNode)item);
      }
      else if (item instanceof AbstractTreeNode) {
        AbstractTreeNode parent = ((AbstractTreeNode)item).getParent();
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
