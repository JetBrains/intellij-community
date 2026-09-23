// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi.frontend.tree;

import com.intellij.execution.dashboard.RunDashboardRunConfigurationStatus;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Konstantin Aleev
 */
public final class RunDashboardStatusFilter {
  private final Set<RunDashboardRunConfigurationStatus> myFilteredStatuses = new HashSet<>();

  public boolean isVisible(AbstractTreeNode<?> node) {
    return !(node instanceof FrontendRunConfigurationNode) || isVisible(((FrontendRunConfigurationNode)node).getStatus());
  }

  public boolean isVisible(@Nullable RunDashboardRunConfigurationStatus status) {
    return !myFilteredStatuses.contains(status);
  }

  public void hide(@NotNull RunDashboardRunConfigurationStatus status) {
    myFilteredStatuses.add(status);
  }

  public void show(@NotNull RunDashboardRunConfigurationStatus status) {
    myFilteredStatuses.remove(status);
  }
}
