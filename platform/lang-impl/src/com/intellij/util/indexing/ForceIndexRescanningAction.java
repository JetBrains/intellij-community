// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.diagnostic.ScanningType;
import org.jetbrains.annotations.NotNull;

final class ForceIndexRescanningAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    UnindexedFilesUpdater task = new UnindexedFilesUpdater(project,
                                                           false,
                                                           false,
                                                           null,
                                                           null,
                                                           "Force re-scanning",
                                                           ScanningType.FULL_FORCED);
    task.queue();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
