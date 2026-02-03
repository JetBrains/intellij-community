// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.chains;

import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementations may override {@link #equals(Object)} and {@link #hashCode()}.
 * These will be used by {@link com.intellij.diff.impl.CacheDiffRequestProcessor} implementations, that might return
 * a different instance of 'same' producer in {@link com.intellij.diff.impl.CacheDiffRequestProcessor#getCurrentRequestProvider()}
 * (ex: {@link com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor}).
 */
public interface DiffRequestProducer {
  @Nls
  @NotNull
  String getName();

  default @Nullable FileType getContentType() {
    return null;
  }

  /*
   * Should be called either in EDT or without ReadLock.
   * Some implementors might need WriteLock, so usage of Application.invokeAndWait() is possible.
   *
   * Valid ModalityState should be passed with ProgressIndicator.getModalityState().
   */
  @RequiresBackgroundThread
  @NotNull
  DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
    throws DiffRequestProducerException, ProcessCanceledException;
}
