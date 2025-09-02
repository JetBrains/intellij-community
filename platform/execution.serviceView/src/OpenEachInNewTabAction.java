// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView;

import com.intellij.execution.services.ServiceViewManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.platform.execution.serviceView.ServiceModel.ServiceViewItem;
import com.intellij.platform.execution.serviceView.ServiceViewDragHelper.ServiceViewDragBean;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

import static com.intellij.platform.execution.serviceView.ServiceViewActionProvider.getSelectedItems;
import static com.intellij.platform.execution.serviceView.ServiceViewActionProvider.getSelectedView;

final class OpenEachInNewTabAction
  extends DumbAwareAction
  implements ActionRemoteBehaviorSpecification.Frontend {

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
    e.getPresentation().setVisible(enabled || !e.isFromContextMenu());
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
