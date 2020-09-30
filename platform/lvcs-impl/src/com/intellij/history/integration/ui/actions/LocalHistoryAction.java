// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration.ui.actions;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public abstract class LocalHistoryAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    LocalHistoryFacade vcs = getVcs();
    IdeaGateway gateway = getGateway();

    e.getPresentation().setEnabled(project != null && vcs != null && gateway != null);
    e.getPresentation().setVisible(project != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    actionPerformed(e.getRequiredData(CommonDataKeys.PROJECT), Objects.requireNonNull(getGateway()), e);
  }

  protected abstract void actionPerformed(@NotNull Project p, @NotNull IdeaGateway gw, @NotNull AnActionEvent e);

  @Nullable
  protected LocalHistoryFacade getVcs() {
    return LocalHistoryImpl.getInstanceImpl().getFacade();
  }

  @Nullable
  protected IdeaGateway getGateway() {
    return LocalHistoryImpl.getInstanceImpl().getGateway();
  }
}
