// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * ExecutorAction has to be left in lang-impl for compatibility.
 * It depends on RunDashboardActionUtils.
 * RunDashboardActionUtils depend on RunDashboardServiceViewContributor and GroupingNode.
 * This interface abstracts RunDashboardServiceViewContributor and GroupingNode and breaks the last dependency.
 */
@Internal
public interface RunDashboardGroupNode {

  @NotNull List<@NotNull Object> getChildren(@NotNull Project project, @NotNull AnActionEvent e);
}
