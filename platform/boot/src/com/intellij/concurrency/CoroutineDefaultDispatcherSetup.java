// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import org.jetbrains.annotations.ApiStatus.Internal;

/**
 * Sets the size of the {@code Dispatchers.Default} thread pool on startup.
 */
@Internal
public final class CoroutineDefaultDispatcherSetup {
  /**
   * The minimum number of CPU workers for {@code Dispatchers.Default}.
   * Set it with {@code -Didea.coroutines.default.min.pool.size=N} or in {@code idea.properties}.
   */
  public static final String MIN_POOL_SIZE_PROPERTY = "idea.coroutines.default.min.pool.size";
  public static final int DEFAULT_MIN_POOL_SIZE = 8;

  private static final String COROUTINE_CORE_POOL_SIZE_PROPERTY = "kotlinx.coroutines.scheduler.core.pool.size";

  private CoroutineDefaultDispatcherSetup() { }

  /**
   * Raises the {@code Dispatchers.Default} pool size to {@link #MIN_POOL_SIZE_PROPERTY} when the machine has fewer processors.
   * Call it before the first access to {@code kotlinx.coroutines.Dispatchers}, because the scheduler reads the property once.
   * An explicit {@code -Dkotlinx.coroutines.scheduler.core.pool.size} JVM option wins.
   */
  public static void setupMinPoolSize() {
    if (System.getProperty(COROUTINE_CORE_POOL_SIZE_PROPERTY) != null) {
      return;
    }
    int minPoolSize = Integer.getInteger(MIN_POOL_SIZE_PROPERTY, DEFAULT_MIN_POOL_SIZE);
    int nCpu = Runtime.getRuntime().availableProcessors();
    if (minPoolSize > nCpu) {
      System.setProperty(COROUTINE_CORE_POOL_SIZE_PROPERTY, String.valueOf(minPoolSize));
    }
  }
}
