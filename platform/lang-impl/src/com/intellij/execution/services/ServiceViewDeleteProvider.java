// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.CommonBundle;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.services.ServiceModel.ServiceViewItem;
import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

class ServiceViewDeleteProvider implements DeleteProvider {
  private final ServiceView myServiceView;

  ServiceViewDeleteProvider(@NotNull ServiceView serviceView) {
    myServiceView = serviceView;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
    Project project = dataContext.getData(CommonDataKeys.PROJECT);
    if (project == null) return;

    List<Pair<ServiceViewItem, Runnable>> items = ContainerUtil.mapNotNull(myServiceView.getSelectedItems(), item -> {
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
    if (!ContainerUtil.exists(myServiceView.getSelectedItems(), item -> item.getViewDescriptor().getRemover() != null)) {
      return false;
    }
    JComponent detailsComponent = myServiceView.getUi().getDetailsComponent();
    return detailsComponent == null || !UIUtil.isAncestor(detailsComponent, dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT));
  }

  @NotNull
  private static List<Pair<ServiceViewItem, Runnable>> filterChildren(List<? extends Pair<ServiceViewItem, Runnable>> items) {
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