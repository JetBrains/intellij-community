/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.AppUIUtil;
import org.jetbrains.annotations.NotNull;

public final class DataSharingOptionsAction extends DumbAwareAction {
  public DataSharingOptionsAction() {
    super(IdeBundle.messagePointer("action.DataSharingOptionsAction.text"),
          IdeBundle.messagePointer("action.DataSharingOptionsAction.description"), null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    try {
      AppUIUtil.confirmConsentOptions(AppUIUtil.loadConsentsForEditing());
    }
    catch (Exception ex) {
      Logger.getInstance(DataSharingOptionsAction.class).warn(ex);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
