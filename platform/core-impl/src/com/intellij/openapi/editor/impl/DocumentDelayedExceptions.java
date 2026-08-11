// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;

/**
 * Collects exceptions thrown by document listeners while notification continues for the remaining listeners.
 */
final class DocumentDelayedExceptions {
  private static final Logger LOG = Logger.getInstance(DocumentDelayedExceptions.class);

  private final boolean shouldLogPCE;
  private Throwable exception;

  DocumentDelayedExceptions(boolean shouldLogPCE) {
    this.shouldLogPCE = shouldLogPCE;
  }

  void register(@NotNull Throwable e) {
    if (exception == null) {
      exception = e;
    } else if (exception != e) { // IJPL-214455 addSuppressed may throw IllegalArgumentException leading to broken editor
      exception.addSuppressed(e);
    }
    if (!(e instanceof ProcessCanceledException)) {
      LOG.error(e);
    } else if (shouldLogPCE) {
      LOG.error(
        "ProcessCanceledException must not be thrown from document listeners for real document",
        new Throwable(e)
      );
    }
  }

  void rethrowPCE() {
    if (exception instanceof ProcessCanceledException) {
      // the case of some wise inspection modifying a non-physical document during highlighting to be interrupted
      throw (ProcessCanceledException)exception;
    }
  }
}
