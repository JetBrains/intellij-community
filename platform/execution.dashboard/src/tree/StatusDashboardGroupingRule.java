// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.tree;

import com.intellij.execution.dashboard.RunDashboardGroup;
import com.intellij.execution.dashboard.RunDashboardGroupingRule;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationStatus;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author konstantin.aleev
 */
public final class StatusDashboardGroupingRule implements RunDashboardGroupingRule {
  public static final @NonNls String NAME = "StatusDashboardGroupingRule";

  @Override
  public @NotNull String getName() {
    return NAME;
  }

  @Override
  public @Nullable RunDashboardGroup getGroup(AbstractTreeNode<?> node) {
    Project project = node.getProject();
    if (project != null && !PropertiesComponent.getInstance(project).getBoolean(getName(), false)) {
      return null;
    }
    if (node instanceof RunDashboardRunConfigurationNode runConfigurationNode) {
      RunDashboardRunConfigurationStatus status = runConfigurationNode.getStatus();
      return new RunDashboardGroupImpl<>(status, status.getName(), status.getIcon());
    }
    return null;
  }
}
