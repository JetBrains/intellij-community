// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;

/**
 * An internal service not supposed to be used directly
 */
public abstract class AsyncExecutionService {
  @NotNull
  protected abstract AppUIExecutor createUIExecutor(@NotNull ModalityState modalityState);

  @NotNull
  protected abstract <T> NonBlockingReadAction<T> buildNonBlockingReadAction(@NotNull Callable<T> computation);

  @NotNull
  static AsyncExecutionService getService() {
    return ServiceManager.getService(AsyncExecutionService.class);
  }
}
