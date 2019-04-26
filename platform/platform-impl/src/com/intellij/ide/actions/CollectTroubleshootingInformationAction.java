// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.troubleshooting.ui.CollectTroubleshootingInformationDialog;
import org.jetbrains.annotations.NotNull;

public class CollectTroubleshootingInformationAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    new CollectTroubleshootingInformationDialog(e.getRequiredData(CommonDataKeys.PROJECT)).show();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(e.getProject() != null);
  }
}
