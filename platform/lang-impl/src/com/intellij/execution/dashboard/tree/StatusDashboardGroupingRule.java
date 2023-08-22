// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard.tree;

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
  @NonNls public static final String NAME = "StatusDashboardGroupingRule";

  @Override
  @NotNull
  public String getName() {
    return NAME;
  }

  @Nullable
  @Override
  public RunDashboardGroup getGroup(AbstractTreeNode<?> node) {
    Project project = node.getProject();
    if (project != null && !PropertiesComponent.getInstance(project).getBoolean(getName(), true)) {
      return null;
    }
    if (node instanceof RunDashboardRunConfigurationNode runConfigurationNode) {
      RunDashboardRunConfigurationStatus status = runConfigurationNode.getStatus();
      return new RunDashboardGroupImpl<>(status, status.getName(), status.getIcon());
    }
    return null;
  }
}
