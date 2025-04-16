// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.dashboard.RunDashboardManager;
import com.intellij.execution.dashboard.RunDashboardNode;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.services.ServiceViewActionUtils;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.platform.execution.dashboard.RunDashboardManagerImpl;
import com.intellij.platform.execution.dashboard.RunDashboardServiceViewContributor;
import com.intellij.platform.execution.dashboard.tree.ConfigurationTypeDashboardGroupingRule;
import com.intellij.platform.execution.dashboard.tree.GroupingNode;
import com.intellij.platform.execution.dashboard.tree.RunDashboardGroupImpl;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class RestoreHiddenConfigurationsAction
  extends DumbAwareAction
  implements ActionRemoteBehaviorSpecification.Frontend {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    Presentation presentation = e.getPresentation();
    if (project == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    if (ActionPlaces.getActionGroupPopupPlace(ActionPlaces.SERVICES_TOOLBAR).equals(e.getPlace())) {
      presentation.setEnabledAndVisible(hasHiddenConfiguration(project));
      presentation.setText(ExecutionBundle.message("run.dashboard.restore.hidden.configurations.toolbar.action.name"));
      return;
    }
    presentation.setText(ExecutionBundle.message("run.dashboard.restore.hidden.configurations.popup.action.name"));
    RunDashboardServiceViewContributor root = ServiceViewActionUtils.getTarget(e, RunDashboardServiceViewContributor.class);
    if (root != null) {
      presentation.setEnabledAndVisible(hasHiddenConfiguration(project));
      return;
    }
    if (!PropertiesComponent.getInstance(project).getBoolean(ConfigurationTypeDashboardGroupingRule.NAME, true)) {
      List<RunDashboardNode> nodes = ServiceViewActionUtils.getTargets(e, RunDashboardNode.class);
      presentation.setEnabledAndVisible(!nodes.isEmpty() && hasHiddenConfiguration(project));
      return;
    }
    Set<ConfigurationType> types = getTargetTypes(e);
    if (types.isEmpty()) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    Set<RunConfiguration> hiddenConfigurations =
      ((RunDashboardManagerImpl)RunDashboardManager.getInstance(project)).getHiddenConfigurations();
    List<RunConfiguration> configurations =
      ContainerUtil.filter(hiddenConfigurations, configuration -> types.contains(configuration.getType()));
    presentation.setEnabledAndVisible(!configurations.isEmpty());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    RunDashboardServiceViewContributor root = ServiceViewActionUtils.getTarget(e, RunDashboardServiceViewContributor.class);
    if (ActionPlaces.getActionGroupPopupPlace(ActionPlaces.SERVICES_TOOLBAR).equals(e.getPlace()) ||
        root != null ||
        !PropertiesComponent.getInstance(project).getBoolean(ConfigurationTypeDashboardGroupingRule.NAME, true)) {
      // Restore all hidden configurations if action is invoked from Services toolbar, or on Run Dashboard contributor root node,
      // or when grouping by configuration type is disabled.
      RunDashboardManagerImpl runDashboardManager = (RunDashboardManagerImpl)RunDashboardManager.getInstance(project);
      runDashboardManager.restoreConfigurations(new HashSet<>(runDashboardManager.getHiddenConfigurations()));
      return;
    }

    Set<ConfigurationType> types = getTargetTypes(e);
    RunDashboardManagerImpl runDashboardManager = (RunDashboardManagerImpl)RunDashboardManager.getInstance(project);
    List<RunConfiguration> configurations =
      ContainerUtil.filter(runDashboardManager.getHiddenConfigurations(), configuration -> types.contains(configuration.getType()));
    runDashboardManager.restoreConfigurations(configurations);
  }

  private static boolean hasHiddenConfiguration(Project project) {
    return !((RunDashboardManagerImpl)RunDashboardManager.getInstance(project)).getHiddenConfigurations().isEmpty();
  }

  private static Set<ConfigurationType> getTargetTypes(AnActionEvent e) {
    List<RunDashboardNode> targets = ServiceViewActionUtils.getTargets(e, RunDashboardNode.class);
    if (targets.isEmpty()) return Collections.emptySet();

    Set<ConfigurationType> types = new HashSet<>();
    for (RunDashboardNode node : targets) {
      if (node instanceof RunDashboardRunConfigurationNode) {
        types.add(((RunDashboardRunConfigurationNode)node).getConfigurationSettings().getType());
      }
      else if (node instanceof GroupingNode) {
        RunDashboardGroupImpl<?> group = (RunDashboardGroupImpl<?>)((GroupingNode)node).getGroup();
        ConfigurationType type = ObjectUtils.tryCast(group.getValue(), ConfigurationType.class);
        if (type == null) {
          return Collections.emptySet();
        }
        types.add(type);
      }
      else {
        return Collections.emptySet();
      }
    }
    return types;
  }
}
