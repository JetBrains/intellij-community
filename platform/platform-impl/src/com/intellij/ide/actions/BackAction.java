
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.ui.navigation.History;
import org.jetbrains.annotations.NotNull;

public final class BackAction extends AnAction implements DumbAware {
  public BackAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    History history = e.getData(History.KEY);

    if (history != null) {
      history.back();
    }
    else if (project != null) {
      IdeDocumentHistory.getInstance(project).back();
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    History history = e.getData(History.KEY);
    boolean isModalContext = e.getData(PlatformCoreDataKeys.IS_MODAL_CONTEXT) == Boolean.TRUE;

    Presentation presentation = e.getPresentation();
    if (history != null) {
      presentation.setEnabled(history.canGoBack());
    }
    else if (project != null && !project.isDisposed()) {
      presentation.setEnabled(!isModalContext && IdeDocumentHistory.getInstance(project).isBackAvailable());
    }
    else {
      presentation.setEnabled(false);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}