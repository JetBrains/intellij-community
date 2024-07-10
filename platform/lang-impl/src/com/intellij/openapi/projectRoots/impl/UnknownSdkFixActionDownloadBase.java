// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class UnknownSdkFixActionDownloadBase extends UnknownSdkFixActionBase {
  @NotNull
  protected abstract UnknownSdkDownloadTask createTask();

  @Override
  public final void applySuggestionAsync(@Nullable Project project) {
    createTask().withListener(getMulticaster()).runAsync(project);
  }

  @NotNull
  @Override
  public final Sdk applySuggestionBlocking(@NotNull ProgressIndicator indicator) {
    return createTask().withListener(getMulticaster()).runBlocking(indicator);
  }
}
