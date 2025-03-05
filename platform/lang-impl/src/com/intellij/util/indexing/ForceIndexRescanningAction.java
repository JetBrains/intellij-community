// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.diagnostic.ScanningType;
import kotlinx.coroutines.CompletableDeferredKt;
import org.jetbrains.annotations.NotNull;

final class ForceIndexRescanningAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    var scanningParameters = CompletableDeferredKt.CompletableDeferred(
      new ScanningIterators(
        "Force re-scanning",
        null,
        null,
        ScanningType.FULL_FORCED
      )
    );
    UnindexedFilesScanner task = new UnindexedFilesScanner(project,
                                                           false,
                                                           false,
                                                           null,
                                                           null,
                                                           null,
                                                           false,
                                                           scanningParameters);
    task.queue();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
