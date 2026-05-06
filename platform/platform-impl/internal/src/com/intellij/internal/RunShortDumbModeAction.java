// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.IndexingBundle;
import org.jetbrains.annotations.NotNull;

final class RunShortDumbModeAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    new MyDumbModeTask().queue(project);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static final class MyDumbModeTask extends DumbModeTask {
    @Override
    public void performInDumbMode(@NotNull ProgressIndicator indicator) {
      indicator.setText(IndexingBundle.message("toggled.dumb.mode"));
      try {
        Thread.sleep(5_000);
      }
      catch (InterruptedException e) {
      }
    }
  }
}
