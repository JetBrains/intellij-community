// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.intellij.platform.execution.serviceView.ServiceViewActionProvider.getSelectedItems;
import static com.intellij.platform.execution.serviceView.ServiceViewActionProvider.getSelectedView;

final class OpenInNewTabActionGroup extends DefaultActionGroup implements DumbAware {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    ServiceView selectedView = getSelectedView(e);
    e.getPresentation().setEnabled(selectedView != null);
    e.getPresentation().putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, selectedView != null &&
                                                                           getSelectedItems(e).size() == 1);
    e.getPresentation().setPerformGroup(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ServiceView selectedView = getSelectedView(e);
    if (selectedView == null) return;

    if (getSelectedItems(e).size() == 1) {
      AnAction[] children = getChildren(e);
      for (AnAction child : children) {
        if (child instanceof OpenInNewTabAction) {
          ActionUtil.performAction(child, e);
          return;
        }
      }
    }

    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(null, this, e.getDataContext(), true, null, 5);
    popup.setAdText(ExecutionBundle.message("service.view.open.in.new.tab.ad.text"), SwingConstants.LEFT);

    if (e.isFromActionToolbar()) {
      Component source = ObjectUtils.tryCast(e.getInputEvent().getSource(), Component.class);
      if (source != null) {
        popup.showUnderneathOf(source);
        return;
      }
    }
    popup.showInBestPositionFor(e.getDataContext());
  }
}
