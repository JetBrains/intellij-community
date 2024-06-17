// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions;

import com.intellij.platform.execution.dashboard.tree.StatusDashboardGroupingRule;
import org.jetbrains.annotations.NotNull;

final class GroupByConfigurationStatusAction extends RunDashboardGroupingRuleToggleAction {

  @Override
  protected @NotNull String getRuleName() {
    return StatusDashboardGroupingRule.NAME;
  }

  @Override
  protected boolean isEnabledByDefault() {
    return false;
  }
}
