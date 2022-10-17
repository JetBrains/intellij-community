// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.dashboard.tree.GroupingNode;
import com.intellij.execution.dashboard.tree.RunDashboardGroupImpl;
import com.intellij.execution.services.ServiceViewContributorDeleteProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class RunDashboardServiceViewDeleteProvider implements ServiceViewContributorDeleteProvider {
  private DeleteProvider myDelegate;

  @Override
  public void setFallbackProvider(DeleteProvider provider) {
    myDelegate = provider;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
    List<ConfigurationType> targetTypes = getTargetTypes(dataContext);
    if (targetTypes.isEmpty()) {
      if (myDelegate != null) {
        myDelegate.deleteElement(dataContext);
      }
      return;
    }

    Project project = dataContext.getData(CommonDataKeys.PROJECT);
    if (project == null) return;

    ConfigurationType onlyType = ContainerUtil.getOnlyItem(targetTypes);
    String message;
    if (onlyType != null) {
      message = ExecutionBundle.message("run.dashboard.remove.run.configuration.type.confirmation", onlyType.getDisplayName());
    }
    else {
      message = ExecutionBundle.message("run.dashboard.remove.run.configuration.types.confirmation", targetTypes.size());
    }

    if (!MessageDialogBuilder.yesNo(IdeBundle.message("button.remove"), message)
          .yesText(IdeBundle.message("button.remove"))
          .icon(Messages.getWarningIcon())
          .ask(project)) {
      return;
    }
    RunDashboardManager runDashboardManager = RunDashboardManager.getInstance(project);
    Set<String> types = new HashSet<>(runDashboardManager.getTypes());
    for (ConfigurationType type : targetTypes) {
      types.remove(type.getId());
    }
    runDashboardManager.setTypes(types);
  }

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    List<ConfigurationType> targetTypes = getTargetTypes(dataContext);
    return !targetTypes.isEmpty() || (myDelegate != null && myDelegate.canDeleteElement(dataContext));
  }

  private static List<ConfigurationType> getTargetTypes(DataContext dataContext) {
    Object[] items = dataContext.getData(PlatformCoreDataKeys.SELECTED_ITEMS);
    if (items == null) return Collections.emptyList();

    List<ConfigurationType> types = new SmartList<>();
    for (Object item : items) {
      if (item instanceof GroupingNode) {
        RunDashboardGroup group = ((GroupingNode)item).getGroup();
        ConfigurationType type = ObjectUtils.tryCast(((RunDashboardGroupImpl<?>)group).getValue(), ConfigurationType.class);
        if (type != null) {
          types.add(type);
          continue;
        }
      }
      return Collections.emptyList();
    }
    return types;
  }
}
