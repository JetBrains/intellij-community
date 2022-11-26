// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Copied from {@link org.jetbrains.idea.maven.aether.RetryProvider}
 */
class Retry {
  private static final Logger LOG = Logger.getLogger(Retry.class.getName());
  private static final Random RANDOM = new Random();
  private static final double EXP_BACKOFF_FACTOR = 2;
  private static final double EXP_BACKOFF_JITTER = 0.3;
  private static final long INITIAL_DELAY_MS = 1000;
  private static final long BACKOFF_LIMIT_MS = TimeUnit.MINUTES.toMillis(15);
  private static final int MAX_ATTEMPTS = 10;

  @FunctionalInterface
  interface ThrowableRunnable<T extends Exception> {
    void run() throws T;
  }

  static void withExponentialBackOff(ThrowableRunnable<?> runnable) throws Exception {
    long effectiveDelay = INITIAL_DELAY_MS;
    for (int i = 1; i <= MAX_ATTEMPTS; i++) {
      try {
        runnable.run();
        return;
      }
      catch (Exception e) {
        if (i == MAX_ATTEMPTS) {
          LOG.info("Retry attempts limit exceeded, tried " + MAX_ATTEMPTS + " times. Cause: " + e.getMessage());
          throw e;
        }
        LOG.info("Attempt " + i + " of " + MAX_ATTEMPTS + " failed, retrying in " + effectiveDelay + "ms. Cause: " + e.getMessage());
        effectiveDelay = exponentialBackOff(effectiveDelay);
      }
    }
    throw new RuntimeException("Should not be reached");
  }

  private static long exponentialBackOff(long effectiveDelayMs) {
    try {
      Thread.sleep(effectiveDelayMs);
    }
    catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Unexpected thread interrupt", ex);
    }

    long nextRawDelay = (long)Math.min(effectiveDelayMs * EXP_BACKOFF_FACTOR, BACKOFF_LIMIT_MS);
    long jitter = (long)(RANDOM.nextDouble() * nextRawDelay * EXP_BACKOFF_JITTER);
    long jitterSign = RANDOM.nextBoolean() ? 1 : -1;
    return nextRawDelay + jitter * jitterSign;
  }
}
