// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi.frontend.tree;

import com.intellij.execution.dashboard.RunDashboardGroup;
import com.intellij.ide.ui.icons.IconIdKt;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardServiceDto;
import com.intellij.platform.execution.dashboard.splitApi.frontend.RunDashboardGroupingRule;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author konstantin.aleev
 */
public final class ConfigurationTypeDashboardGroupingRule implements RunDashboardGroupingRule {
  public static final @NonNls String NAME = "ConfigurationTypeDashboardGroupingRule";

  @Override
  public @NotNull String getName() {
    return NAME;
  }

  @Override
  public @Nullable RunDashboardGroup getGroup(FrontendRunConfigurationNode node) {
    Project project = node.getProject();
    // TODO get value from backend
    if (project != null && !PropertiesComponent.getInstance(project).getBoolean(getName(), true)) {
      return null;
    }
    RunDashboardServiceDto runConfiguration = node.getService();
    return new RunDashboardGroupImpl<>(runConfiguration.getTypeId(), runConfiguration.getTypeDisplayName(),
                                       IconIdKt.icon(runConfiguration.getTypeIconId()));
  }
}
