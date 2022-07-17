// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.aether;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Random;

/**
 * Retry utils can be used to set up dependency resolver
 */
public final class RetryProvider {
  /* Exponential backoff retry requirements */
  private static final Random RANDOM = new Random();
  private static final double EXP_BACKOFF_FACTOR = 2;
  private static final double EXP_BACKOFF_JITTER = 0.3;

  private static final Retry DISABLED_SINGLETON = new Retry() {
    @Override
    public <R> R retry(@NotNull ThrowingSupplier<? extends R> supplier, @NotNull Logger logger) throws Exception {
      return supplier.get();
    }
  };

  /**
   * Get disabled Retry.
   *
   * @return Retry implementation that tries only once to perform a task.
   */
  public static Retry disabled() {
    return DISABLED_SINGLETON;
  }

  /**
   * Get retry with exponential back off.
   *
   * @param initialDelayMs Delay before the first retry after fail in milliseconds.
   * @param backoffLimitMs Limit of delay should not grow upper than in milliseconds.
   * @param maxAttempts    Max attempts to do a job.
   * @return Retry implementation with delay between attempts.
   */
  public static Retry withExponentialBackOff(long initialDelayMs, long backoffLimitMs, int maxAttempts) {
    if (initialDelayMs <= 0 || backoffLimitMs <= 0 || maxAttempts <= 0) {
      throw new IllegalArgumentException(
        "Wrong arguments provided: initialDelayMs=" + initialDelayMs + "backoffLimitMs=" + backoffLimitMs + " maxAttempts=" + maxAttempts);
    }

    return new Retry() {
      @Override
      public <R> R retry(@NotNull ThrowingSupplier<? extends R> supplier, @NotNull Logger logger) throws Exception {
        return exponentialBackOffRetry(initialDelayMs, backoffLimitMs, maxAttempts, supplier, logger);
      }
    };
  }


  /**
   * Utility class - should not be instantiated
   */
  private RetryProvider() {
  }

  /**
   * Retry with exponential back off implementation.
   *
   * @param initialDelayMs Delay before the first retry after fail in milliseconds.
   * @param backoffLimitMs Limit of delay should not grow upper than in milliseconds.
   * @param maxAttempts    Max attempts to do a job.
   * @param supplier       Supplies that does some possibly throwing work.
   * @param logger         Messages logger.
   * @param <R>            Supplier result type.
   * @return Result from supplier.
   * @throws Exception An error the job thrown if attempts limit exceeded.
   */
  private static <R> R exponentialBackOffRetry(long initialDelayMs,
                                               long backoffLimitMs,
                                               int maxAttempts,
                                               @NotNull ThrowingSupplier<? extends R> supplier,
                                               @NotNull Logger logger) throws Exception {
    long effectiveDelay = initialDelayMs;

    for (int i = 1; i <= maxAttempts; i++) {
      try {
        return supplier.get();
      }
      catch (Exception e) {
        if (i == maxAttempts) {
          logger.info("Retry attempts limit exceeded, tried " + maxAttempts + " times. Cause: " + e.getMessage());
          throw e;
        }
        logger.info("Attempt " + i + " of " + maxAttempts + " failed, retrying in " + effectiveDelay + "ms. Cause: " + e.getMessage());
        effectiveDelay = exponentialBackOff(effectiveDelay, backoffLimitMs);
      }
    }
    throw new RuntimeException("Should not be reached");
  }

  /**
   * Exponential back off for retry. Sleeps current thread for {@code effectiveDelayMs},
   * calculates next delay.
   *
   * @param effectiveDelayMs Effective delay to sleep.
   * @param backoffLimitMs   Limit of delay should not grow upper than in milliseconds.
   * @return Next effective delay.
   */
  private static long exponentialBackOff(long effectiveDelayMs, long backoffLimitMs) {
    try {
      Thread.sleep(effectiveDelayMs);
    }
    catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Unexpected thread interrupt", ex);
    }

    long nextRawDelay = (long)Math.min(effectiveDelayMs * EXP_BACKOFF_FACTOR, backoffLimitMs);
    long jitter = (long)(RANDOM.nextDouble() * nextRawDelay * EXP_BACKOFF_JITTER);
    long jitterSign = RANDOM.nextBoolean() ? 1 : -1;
    return nextRawDelay + jitter * jitterSign;
  }
}
