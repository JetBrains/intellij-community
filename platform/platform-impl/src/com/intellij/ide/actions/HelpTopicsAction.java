// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public final class HelpTopicsAction extends HelpActionBase implements DumbAware, LightEditCompatible, ActionRemoteBehaviorSpecification.Frontend {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    HelpManager.getInstance().invokeHelp("top");
  }

  @Override
  public boolean isAvailable() {
    return ApplicationInfo.helpAvailable();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
