// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import com.intellij.execution.services.ServiceModel.ServiceViewItem;
import com.intellij.execution.services.ServiceViewDragHelper.ServiceViewDragBean;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

import static com.intellij.execution.services.ServiceViewActionProvider.getSelectedItems;
import static com.intellij.execution.services.ServiceViewActionProvider.getSelectedView;

final class OpenEachInNewTabAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean enabled = e.getProject() != null &&
                      getSelectedView(e) != null &&
                      !getSelectedItems(e).isEmpty();
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled || !ActionPlaces.isPopupPlace(e.getPlace()));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    ServiceView serviceView = getSelectedView(e);
    if (serviceView == null) return;

    ServiceViewManagerImpl manager = (ServiceViewManagerImpl)ServiceViewManager.getInstance(project);
    for (ServiceViewItem item : getSelectedItems(e)) {
      manager.extract(new ServiceViewDragBean(serviceView, Collections.singletonList(item)));
    }
  }
}
