// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public class JetBrainsTvAction extends AnAction implements DumbAware {
  private final String myUrl;

  public JetBrainsTvAction() {
    myUrl = ApplicationInfo.getInstance().getJetBrainsTvUrl();
    getTemplatePresentation().setText(() -> ActionsBundle.message("action.Help.JetBrainsTV.templateText", ApplicationNamesInfo.getInstance().getFullProductName()));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean enabled = myUrl != null;
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (myUrl != null) {
      BrowserUtil.browse(myUrl);
    }
  }
}