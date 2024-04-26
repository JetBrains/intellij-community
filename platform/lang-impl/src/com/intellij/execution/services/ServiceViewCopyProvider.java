// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import com.intellij.execution.services.ServiceModel.ServiceViewItem;
import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;

final class ServiceViewCopyProvider implements CopyProvider {
  private final ServiceView myServiceView;

  ServiceViewCopyProvider(@NotNull ServiceView serviceView) {
    myServiceView = serviceView;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    List<ServiceViewItem> items = ServiceViewActionProvider.getSelectedItems(dataContext);
    if (!items.isEmpty()) {
      CopyPasteManager.getInstance().setContents(new StringSelection(
        StringUtil.join(items, item -> ServiceViewDragHelper.getDisplayName(item.getViewDescriptor().getPresentation()), "\n")));
    }
  }

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    if (ServiceViewActionProvider.getSelectedItems(dataContext).isEmpty()) {
      return false;
    }
    JComponent detailsComponent = myServiceView.getUi().getDetailsComponent();
    return detailsComponent == null || !UIUtil.isAncestor(detailsComponent, dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT));
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return false;
  }
}