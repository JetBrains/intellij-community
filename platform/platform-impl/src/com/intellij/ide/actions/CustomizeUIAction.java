// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.ui.customization.CustomizationConfigurable;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.options.ShowSettingsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Internal
public final class CustomizeUIAction extends AnAction {
  public CustomizeUIAction() {
    super(ActionsBundle.messagePointer("action.CustomizeUIAction.text"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ShowSettingsUtil.getInstance().showSettingsDialog(e.getData(CommonDataKeys.PROJECT), CustomizationConfigurable.class);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
