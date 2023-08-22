// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard.tree;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.dashboard.RunDashboardGroup;
import com.intellij.execution.dashboard.RunDashboardGroupingRule;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author konstantin.aleev
 */
public final class ConfigurationTypeDashboardGroupingRule implements RunDashboardGroupingRule {
  @NonNls public static final String NAME = "ConfigurationTypeDashboardGroupingRule";

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
    if (node instanceof RunDashboardRunConfigurationNode) {
      RunnerAndConfigurationSettings configurationSettings = ((RunDashboardRunConfigurationNode)node).getConfigurationSettings();
      ConfigurationType type = configurationSettings.getType();
      return new RunDashboardGroupImpl<>(type, type.getDisplayName(), type.getIcon());
    }
    return null;
  }
}
