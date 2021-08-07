// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jetbrains.annotations.NotNull;

final class OnlyOnceExceptionLogger {
  @NotNull
  private final Logger myLogger;
  @NotNull
  private final IntOpenHashSet myReportedHashes = new IntOpenHashSet();

  OnlyOnceExceptionLogger(@NotNull Logger logger) {myLogger = logger;}

  void info(@NotNull String message, @NotNull Throwable throwable) {
    int hash = ThrowableInterner.computeAccurateTraceHashCode(throwable);

    boolean added;
    synchronized (myReportedHashes) {
      added = myReportedHashes.add(hash);
    }

    if (added) {
      myLogger.info(message + " (trace_hash = " + hash + ")", throwable);
    }
    else {
      myLogger.info(message + " (stacktrace has been already reported with trace_hash = " + hash + ")");
    }
  }
}
