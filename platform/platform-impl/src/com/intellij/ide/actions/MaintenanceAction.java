// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import org.jetbrains.annotations.NotNull;

final class MaintenanceAction extends AnAction implements DumbAware, LightEditCompatible {
  public MaintenanceAction() {
    super(ActionsBundle.messagePointer("action.MaintenanceAction.text"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("MaintenanceGroup");
    JBPopupFactory.getInstance().
      createActionGroupPopup(IdeBundle.message("popup.title.maintenance"), group, e.getDataContext(), JBPopupFactory.ActionSelectionAid.NUMBERING, true).
      showInFocusCenter();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}