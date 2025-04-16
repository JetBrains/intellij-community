// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions;

import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.platform.execution.dashboard.tree.ConfigurationTypeDashboardGroupingRule;
import org.jetbrains.annotations.NotNull;

final class GroupByConfigurationTypeAction
  extends RunDashboardGroupingRuleToggleAction
  implements ActionRemoteBehaviorSpecification.Frontend {

  @Override
  protected @NotNull String getRuleName() {
    return ConfigurationTypeDashboardGroupingRule.NAME;
  }
}
