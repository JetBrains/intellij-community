// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

final class DumpConfigurationTypesAction extends AnAction implements DumbAware {

  DumpConfigurationTypesAction() {
    super(ActionsBundle.messagePointer("action.DumpConfigurationTypesAction.text"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    for (ConfigurationType factory : ConfigurationType.CONFIGURATION_TYPE_EP.getExtensionList()) {
      System.out.println(factory.getDisplayName() + " : " + factory.getId());
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null);
  }
}