// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface ExternalSystemTaskUnderProgress {

  default @Nullable Object getId() {
    return null;
  }

  void execute(@NotNull ProgressIndicator indicator);

  static void executeTaskUnderProgress(
    @NotNull Project project,
    @NotNull @Nls String title,
    @NotNull ProgressExecutionMode progressExecutionMode,
    @NotNull ExternalSystemTaskUnderProgress task
  ) {
    switch (progressExecutionMode) {
      case NO_PROGRESS_SYNC -> task.execute(new EmptyProgressIndicator());
      case MODAL_SYNC -> new Task.Modal(project, title, true) {
        @Override
        public @Nullable Object getId() {
          return task.getId();
        }

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          task.execute(indicator);
        }
      }.queue();
      case NO_PROGRESS_ASYNC -> ApplicationManager.getApplication().executeOnPooledThread(
        () -> task.execute(new EmptyProgressIndicator())
      );
      case IN_BACKGROUND_ASYNC -> new Task.Backgroundable(project, title) {
        @Override
        public @Nullable Object getId() {
          return task.getId();
        }

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          task.execute(indicator);
        }
      }.queue();
      case START_IN_FOREGROUND_ASYNC -> new Task.Backgroundable(project, title, true, PerformInBackgroundOption.DEAF) {
        @Override
        public @Nullable Object getId() {
          return task.getId();
        }

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          task.execute(indicator);
        }
      }.queue();
    }
  }
}
