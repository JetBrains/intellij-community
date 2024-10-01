// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.OldDirectoryCleaner;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class DeleteOldAppDirectoriesAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    new Task.Backgroundable(e.getProject(), IdeBundle.message("old.dirs.action.progress")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        new OldDirectoryCleaner(0).seekAndDestroy(e.getProject(), indicator);
      }
    }.queue();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
