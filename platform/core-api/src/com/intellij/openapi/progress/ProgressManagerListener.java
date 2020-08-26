// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ProgressManagerListener {
  void beforeExecuteUnderProgress(@NotNull Runnable process, ProgressIndicator progress);
  void afterExecuteUnderProgress(@NotNull Runnable process, ProgressIndicator progress);
  void onTaskRunnableCreated(@NotNull Task task, @NotNull ProgressIndicator indicator, @Nullable Runnable continuation);
  void onTaskFinished(@NotNull Task task, boolean canceled, @Nullable Throwable error);
}
