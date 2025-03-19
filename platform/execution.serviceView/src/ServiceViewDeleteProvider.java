// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView;

import com.intellij.CommonBundle;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.services.ServiceViewDefaultDeleteProvider;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.platform.execution.serviceView.ServiceModel.ServiceViewItem;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

final class ServiceViewDeleteProvider implements ServiceViewDefaultDeleteProvider {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
    Project project = dataContext.getData(CommonDataKeys.PROJECT);
    if (project == null) return;

    List<ServiceViewItem> selectedItems = ServiceViewActionProvider.getSelectedItems(dataContext);
    List<Pair<ServiceViewItem, Runnable>> items = ContainerUtil.mapNotNull(selectedItems, item -> {
      Runnable remover = item.getViewDescriptor().getRemover();
      return remover == null ? null : Pair.create(item, remover);
    });
    items = filterChildren(items);
    if (items.isEmpty()) return;

    if (Messages.showYesNoDialog(project,
                                 ExecutionBundle.message("service.view.delete.confirmation",
                                                         ExecutionBundle.message("service.view.items", items.size())),
                                 CommonBundle.message("button.delete"),
                                 Messages.getWarningIcon())
        != Messages.YES) {
      return;
    }
    for (Pair<ServiceViewItem, Runnable> item : items) {
      item.second.run();
    }
  }

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    List<ServiceViewItem> selectedItems = ServiceViewActionProvider.getSelectedItems(dataContext);
    if (!ContainerUtil.exists(selectedItems, item -> item.getViewDescriptor().getRemover() != null)) {
      return false;
    }
    ServiceView selectedView = ServiceViewActionProvider.getSelectedView(dataContext);
    if (selectedView == null) return false;
    JComponent detailsComponent = selectedView.getUi().getDetailsComponent();
    return detailsComponent == null || !UIUtil.isAncestor(detailsComponent, dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT));
  }

  private static @NotNull List<Pair<ServiceViewItem, Runnable>> filterChildren(List<? extends Pair<ServiceViewItem, Runnable>> items) {
    return ContainerUtil.filter(items, item -> {
      ServiceViewItem parent = item.first.getParent();
      while (parent != null) {
        for (Pair<ServiceViewItem, Runnable> pair : items) {
          if (pair.first.equals(parent)) {
            return false;
          }
        }
        parent = parent.getParent();
      }
      return true;
    });
  }
}