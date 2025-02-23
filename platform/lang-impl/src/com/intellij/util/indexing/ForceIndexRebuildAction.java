// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.MessageDialogBuilder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ForceIndexRebuildAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    //noinspection DialogTitleCapitalization
    boolean exitConfirmed = ApplicationManager.getApplication().isUnitTestMode() ||
                            MessageDialogBuilder.okCancel(e.getPresentation().getText(), e.getPresentation().getText()).ask(e.getProject());
    if (!exitConfirmed) {
      return;
    }
    FileBasedIndexTumbler tumbler = new FileBasedIndexTumbler("Force index rebuild");
    tumbler.turnOff();
    try {
      CorruptionMarker.requestInvalidation();
    }
    finally {
      tumbler.turnOn();
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
