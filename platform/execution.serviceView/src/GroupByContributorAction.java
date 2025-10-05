// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import static com.intellij.platform.execution.serviceView.ServiceViewActionProvider.getSelectedView;

final class GroupByContributorAction extends ToggleAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    ServiceView selectedView = getSelectedView(e);
    e.getPresentation().setEnabled(selectedView != null);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    ServiceView selectedView = getSelectedView(e);
    return selectedView != null && selectedView.isGroupByContributor();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    ServiceView selectedView = getSelectedView(e);
    if (selectedView != null) {
      selectedView.setGroupByContributor(state);
    }
  }
}
