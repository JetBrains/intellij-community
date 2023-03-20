// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard.actions;

import com.intellij.execution.dashboard.tree.ConfigurationTypeDashboardGroupingRule;
import org.jetbrains.annotations.NotNull;

final class GroupByConfigurationTypeAction extends RunDashboardGroupingRuleToggleAction {

  @Override
  protected @NotNull String getRuleName() {
    return ConfigurationTypeDashboardGroupingRule.NAME;
  }
}
