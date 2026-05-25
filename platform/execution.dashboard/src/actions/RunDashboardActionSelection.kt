// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions;

import com.intellij.execution.dashboard.LegacyRunDashboardServiceSubstitutor;
import com.intellij.execution.dashboard.RunDashboardManager;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.dashboard.RunDashboardService;
import com.intellij.execution.dashboard.actions.RunDashboardGroupNode;
import com.intellij.execution.services.ServiceViewActionUtils;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.platform.ide.productMode.IdeProductMode;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class RunDashboardActionSelection {
  private RunDashboardActionSelection() {
  }

  static @Nullable RunDashboardRunConfigurationNode getTarget(@NotNull AnActionEvent e) {
    return ServiceViewActionUtils.getTarget(e, RunDashboardRunConfigurationNode.class);
  }

  static @NotNull JBIterable<RunDashboardRunConfigurationNode> getLeafTargets(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return JBIterable.empty();

    var uiSelection = e.getData(PlatformCoreDataKeys.SELECTED_ITEMS);
    if (uiSelection != null) {
      return getLeafTargetsFromUiSelection(e, project, uiSelection);
    }
    return getFallbackSelectionForEmbeddedBackendRunToolwindowActions(e, project);
  }

  private static @NotNull JBIterable<RunDashboardRunConfigurationNode> getLeafTargetsFromUiSelection(@NotNull AnActionEvent e,
                                                                                                     Project project,
                                                                                                     Object uiSelection) {
    Set<RunDashboardRunConfigurationNode> result = new LinkedHashSet<>();
    if (!collectLeaves(project, e, JBIterable.of(uiSelection).toList(), result)) return JBIterable.empty();

    JBIterable<RunDashboardRunConfigurationNode> selectedNodes = JBIterable.from(result);
    if (!IdeProductMode.isMonolith()) return selectedNodes;

    var substitutor = ContainerUtil.getFirstItem(LegacyRunDashboardServiceSubstitutor.EP_NAME.getExtensionList());
    return substitutor == null
           ? selectedNodes
           : JBIterable.from(ContainerUtil.map(result, it -> substitutor.substituteWithBackendService(it, project)));
  }

  private static @NotNull JBIterable<RunDashboardRunConfigurationNode> getFallbackSelectionForEmbeddedBackendRunToolwindowActions(@NotNull AnActionEvent e,
                                                                                                                                  Project project) {
    var currentContentDescriptor = e.getData(LangDataKeys.RUN_CONTENT_DESCRIPTOR);
    var currentContentDescriptorId = currentContentDescriptor == null ? null : currentContentDescriptor.getId();
    if (currentContentDescriptorId == null) return JBIterable.empty();

    // Backend case with a non-split Run tool window where frontend data context serialization does not carry Service View selection.
    var maybeService = RunDashboardManager.getInstance(project).findService(currentContentDescriptorId);
    RunDashboardRunConfigurationNode selectedService = maybeService instanceof RunDashboardService service ? service : null;
    if (selectedService == null) return JBIterable.empty();

    return JBIterable.of(selectedService);
  }

  private static boolean collectLeaves(@NotNull Project project,
                                       @NotNull AnActionEvent e,
                                       @NotNull List<?> items,
                                       @NotNull Set<? super RunDashboardRunConfigurationNode> result) {
    for (Object item : items) {
      if (item instanceof RunDashboardGroupNode groupNode) {
        if (!collectLeaves(project, e, groupNode.getChildren(project, e), result)) return false;
      }
      else if (item instanceof RunDashboardRunConfigurationNode node) {
        result.add(node);
      }
      else if (item instanceof AbstractTreeNode<?> treeNode && treeNode.getParent() instanceof RunDashboardRunConfigurationNode node) {
        result.add(node);
      }
      else {
        return false;
      }
    }
    return true;
  }
}
