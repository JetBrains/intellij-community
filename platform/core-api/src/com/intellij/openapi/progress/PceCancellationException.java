// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CancellationException;

/**
 * {@link ProcessCanceledException} to {@link CancellationException} adapter.
 *
 * @see CeProcessCanceledException
 */
@Internal
public final class PceCancellationException extends CancellationException {

  public PceCancellationException(@NotNull ProcessCanceledException pce) {
    initCause(pce);
  }

  @Override
  public synchronized @NotNull ProcessCanceledException getCause() {
    return (ProcessCanceledException)super.getCause();
  }
}
