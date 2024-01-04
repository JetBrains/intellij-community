// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.DumbAware;
import com.intellij.platform.ide.customization.ExternalProductResourceUrls;
import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;

public final class JetBrainsTvAction extends HelpActionBase implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  public JetBrainsTvAction() {
    getTemplatePresentation().setText(() -> ActionsBundle.message("action.Help.JetBrainsTV.templateText", ApplicationNamesInfo.getInstance().getFullProductName()));
  }

  @Override
  public boolean isAvailable() {
    return ExternalProductResourceUrls.getInstance().getYouTubeChannelUrl() != null;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Url url = ExternalProductResourceUrls.getInstance().getYouTubeChannelUrl();
    if (url != null) {
      BrowserUtil.browse(url.toExternalForm());
    }
  }
}