// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


public class DevelopPluginsAction extends AnAction implements DumbAware {
  private static final @NonNls String PLUGIN_WEBSITE = "https://plugins.jetbrains.com/docs/intellij/";

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    try {
      BrowserUtil.browse(PLUGIN_WEBSITE);
    }
    catch(IllegalStateException ex) {
      // ignore
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}