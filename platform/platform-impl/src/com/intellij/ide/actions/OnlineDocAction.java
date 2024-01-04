// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.platform.ide.customization.ExternalProductResourceUrls;
import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;

public final class OnlineDocAction extends HelpActionBase implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Url url = ExternalProductResourceUrls.getInstance().getGettingStartedPageUrl();
    if (url != null) {
      BrowserUtil.browse(url.toExternalForm());
    }
  }

  @Override
  public boolean isAvailable() {
    return ExternalProductResourceUrls.getInstance().getGettingStartedPageUrl() != null;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
