// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.history.integration.ui.actions;

import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.history.integration.ui.actions.ShowLocalHistoryUtilKt.canShowLocalHistoryFor;
import static com.intellij.history.integration.ui.actions.ShowLocalHistoryUtilKt.showLocalHistoryFor;

@ApiStatus.Internal
public class ShowHistoryAction extends LocalHistoryAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    Presentation presentation = e.getPresentation();
    if (presentation.isEnabled()) {
      IdeaGateway gateway = getGateway();
      Collection<VirtualFile> files = getFiles(e);
      presentation.setEnabled(isActionEnabled(gateway, files));
    }
  }

  @Override
  protected void actionPerformed(@NotNull Project p, @NotNull IdeaGateway gw, @NotNull AnActionEvent e) {
    showLocalHistoryFor(p, gw, getFiles(e));
  }

  protected boolean isActionEnabled(@NotNull IdeaGateway gw, @NotNull Collection<VirtualFile> files) {
    return canShowLocalHistoryFor(gw, files);
  }

  protected @NotNull Collection<VirtualFile> getFiles(@NotNull AnActionEvent e) {
    return JBIterable.from(e.getData(VcsDataKeys.VIRTUAL_FILES)).toSet();
  }
}
