// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard.actions;

import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.dashboard.tree.GroupingNode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.containers.TreeTraversal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class RunDashboardActionUtils {
  private RunDashboardActionUtils() {
  }

  @NotNull
  static JBIterable<RunDashboardRunConfigurationNode> getTargets(@NotNull AnActionEvent e) {
    return getTargets(e, RunDashboardRunConfigurationNode.class);
  }

  @Nullable
  static RunDashboardRunConfigurationNode getTarget(@NotNull AnActionEvent e) {
    Object[] items = e.getData(PlatformDataKeys.SELECTED_ITEMS);
    if (items == null || items.length != 1 || !(items[0] instanceof RunDashboardRunConfigurationNode)) return null;

    return (RunDashboardRunConfigurationNode)items[0];
  }

  @NotNull
  static <T> JBIterable<T> getTargets(@NotNull AnActionEvent e, @NotNull Class<T> clazz) {
    Object[] items = e.getData(PlatformDataKeys.SELECTED_ITEMS);
    if (items == null) return JBIterable.empty();

    List<T> result = new ArrayList<>();
    for (Object item : items) {
      if (!clazz.isInstance(item)) {
        return JBIterable.empty();
      }
      result.add(clazz.cast(item));
    }
    return JBIterable.from(result);
  }

  @NotNull
  static JBIterable<RunDashboardRunConfigurationNode> getLeafTargets(@NotNull AnActionEvent e) {
    JBIterable<Object> roots = JBIterable.of(e.getData(PlatformDataKeys.SELECTED_ITEMS));
    JBIterable<Object> leaves = JBTreeTraverser.from(o -> o instanceof GroupingNode ? ((GroupingNode)o).getChildren() : null)
      .withRoots(roots)
      .traverse(TreeTraversal.LEAVES_DFS)
      .unique();
    if (leaves.filter(leaf -> !(leaf instanceof RunDashboardRunConfigurationNode)).isNotEmpty()) return JBIterable.empty();

    return leaves.filter(RunDashboardRunConfigurationNode.class);
  }
}
