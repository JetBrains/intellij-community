// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard.actions;

import com.intellij.execution.dashboard.*;
import com.intellij.execution.services.ServiceViewActionUtils;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.platform.ide.productMode.IdeProductMode;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.execution.dashboard.RunDashboardServiceIdKt.SELECTED_DASHBOARD_SERVICE_ID;
import static com.intellij.execution.dashboard.RunDashboardServiceIdKt.findValue;

@Internal
@Deprecated(forRemoval = true)
public final class RunDashboardActionUtils {
  private RunDashboardActionUtils() {
  }

  public static @NotNull List<RunDashboardRunConfigurationNode> getTargets(@NotNull AnActionEvent e) {
    return ServiceViewActionUtils.getTargets(e, RunDashboardRunConfigurationNode.class);
  }

  public static @Nullable RunDashboardRunConfigurationNode getTarget(@NotNull AnActionEvent e) {
    return ServiceViewActionUtils.getTarget(e, RunDashboardRunConfigurationNode.class);
  }

  public static @NotNull JBIterable<RunDashboardRunConfigurationNode> getLeafTargets(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return JBIterable.empty();

    Set<RunDashboardRunConfigurationNode> result = new LinkedHashSet<>();

    RunDashboardService selectedService = null;

    // todo introduce proper backend node ids, drop selected items usage completely
    if (IdeProductMode.isMonolith()) {
      var uiSelection = e.getData(PlatformCoreDataKeys.SELECTED_ITEMS);
      if (uiSelection != null) {
        JBIterable<Object> roots = JBIterable.of(uiSelection);
        if (!getLeaves(project, e, roots.toList(), Collections.emptyList(), result)) return JBIterable.empty();

        var substitutor = ContainerUtil.getFirstItem(LegacyRunDashboardServiceSubstitutor.EP_NAME.getExtensionList());
        if (substitutor == null) return JBIterable.empty();

        return JBIterable.from(ContainerUtil.map(result, it -> substitutor.substituteWithBackendService(it, project)));
      }
    }

    var currentContentDescriptor = e.getData(LangDataKeys.RUN_CONTENT_DESCRIPTOR);
    var currentContentDescriptorId = currentContentDescriptor == null ? null : currentContentDescriptor.getId();
    if (currentContentDescriptorId != null) {
      // backend case with run toolwindow that is not split in any way and does not properly receive a serialized data context from frontend
      // because of obscure content manager-related wrapping mechanism
      var maybeService = RunDashboardManager.getInstance(project).findService(currentContentDescriptorId);
      selectedService = maybeService instanceof RunDashboardService ? (RunDashboardService)maybeService : null;
    }
    if (selectedService == null) {
      var selectedServiceId = e.getData(SELECTED_DASHBOARD_SERVICE_ID);
      selectedService = selectedServiceId == null ? null : findValue(selectedServiceId);
    }

    JBIterable<Object> roots = JBIterable.of(selectedService);
    if (!getLeaves(project, e, roots.toList(), Collections.emptyList(), result)) return JBIterable.empty();

    return JBIterable.from(result).filter(RunDashboardRunConfigurationNode.class);
  }

  private static boolean getLeaves(Project project, AnActionEvent e, List<Object> items, List<Object> valueSubPath,
                                   Set<? super RunDashboardRunConfigurationNode> result) {
    for (Object item : items) {
      if (item instanceof RunDashboardGroupNode groupNode) {
        List<Object> itemSubPath = new ArrayList<>(valueSubPath);
        itemSubPath.add(item);
        List<Object> children = groupNode.getChildren(project, e);
        if (!getLeaves(project, e, children, itemSubPath, result)) {
          return false;
        }
      }
      else if (item instanceof RunDashboardRunConfigurationNode) {
        result.add((RunDashboardRunConfigurationNode)item);
      }
      else if (item instanceof AbstractTreeNode) {
        AbstractTreeNode<?> parent = ((AbstractTreeNode<?>)item).getParent();
        if (parent instanceof RunDashboardRunConfigurationNode) {
          result.add((RunDashboardRunConfigurationNode)parent);
        }
        else {
          return false;
        }
      }
      else {
        return false;
      }
    }
    return true;
  }
}
