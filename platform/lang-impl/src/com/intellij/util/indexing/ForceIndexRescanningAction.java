// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
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
                                                           "Force re-scanning");
    task.queue(project);
  }
}
