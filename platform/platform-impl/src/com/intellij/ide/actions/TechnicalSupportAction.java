// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.platform.ide.customization.ExternalProductResourceUrls;
import com.intellij.util.Url;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;

public final class TechnicalSupportAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setVisible(ExternalProductResourceUrls.getInstance().getTechnicalSupportUrl() != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Function1<String, Url> urlFunction = ExternalProductResourceUrls.getInstance().getTechnicalSupportUrl();
    if (urlFunction == null) return;
    Project project = e.getProject();
    String description = SendFeedbackAction.getDescription(project);
    BrowserUtil.browse(urlFunction.invoke(description).toExternalForm(), project);
  }
}
