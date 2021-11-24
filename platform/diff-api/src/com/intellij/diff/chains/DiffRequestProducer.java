/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.chains;

import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

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
