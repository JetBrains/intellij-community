// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.EmptyRunnable;

import java.util.concurrent.TimeUnit;

public final class TimingLog {
  public static final Logger LOG = Logger.getInstance(TimingLog.class);

  public static Runnable startActivity(final String name) {
    if (!LOG.isDebugEnabled()) {
      return EmptyRunnable.INSTANCE;
    }

    long start = System.nanoTime();
    return () -> LOG.debug(name + " in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + "ms");
  }
}
