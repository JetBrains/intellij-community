// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public final class OnlineDocAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    BrowserUtil.browse(ApplicationInfoImpl.getShadowInstance().getDocumentationUrl());
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(ApplicationInfoImpl.getShadowInstance().getDocumentationUrl() != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
