// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.merge;

import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jetbrains.annotations.NotNull;

public interface MergeRequestProducer {
  @NotNull
  String getName();

  @RequiresBackgroundThread
  @NotNull
  MergeRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
    throws DiffRequestProducerException, ProcessCanceledException;
}
