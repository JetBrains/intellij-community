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

class RunDashboardActionUtils {
  private RunDashboardActionUtils() {
  }

  @NotNull
  static JBIterable<RunDashboardRunConfigurationNode> getTargets(@NotNull AnActionEvent e) {
    return getTargets(e, RunDashboardRunConfigurationNode.class);
  }

  @NotNull
  static <T> JBIterable<T> getTargets(@NotNull AnActionEvent e, @NotNull Class<T> clazz) {
    return JBIterable.of(e.getData(PlatformDataKeys.SELECTED_ITEMS)).filter(clazz);
  }

  @NotNull
  static JBIterable<RunDashboardRunConfigurationNode> getLeafTargets(@NotNull AnActionEvent e) {
    JBIterable<Object> roots = JBIterable.of(e.getData(PlatformDataKeys.SELECTED_ITEMS));
    return JBTreeTraverser.from(o -> o instanceof GroupingNode ? ((GroupingNode)o).getChildren() : null)
      .withRoots(roots)
      .traverse(TreeTraversal.LEAVES_DFS)
      .filter(RunDashboardRunConfigurationNode.class)
      .unique();
  }
}
