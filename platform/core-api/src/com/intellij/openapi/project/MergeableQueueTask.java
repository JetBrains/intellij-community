// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface MergeableQueueTask<T extends MergeableQueueTask<T>> extends Disposable {
  @Nullable T tryMergeWith(@NotNull T taskFromQueue);

  void perform(@NotNull ProgressIndicator indicator);
}