// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.troubleshooting.ui.CollectTroubleshootingInformationDialog;
import org.jetbrains.annotations.NotNull;

public final class CollectTroubleshootingInformationAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    new CollectTroubleshootingInformationDialog(e.getRequiredData(CommonDataKeys.PROJECT)).show();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(e.getProject() != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
