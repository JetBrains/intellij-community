// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl.helpers;

import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import org.HdrHistogram.Histogram;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Monitors given {@link ReentrantReadWriteLock} usage: regularly samples the lock, calculates utilization metrics:
 * competingThreads = {@link ReentrantReadWriteLock#getQueueLength()} (threads waiting for acquire the lock) + current
 * lock usage, which is either 1 if lock is write-locked, or {@link ReentrantReadWriteLock#getReadLockCount()} if
 * lock is read-locked. Reports avg/90%/max of this usage as OTel metrics.
 * <p>
 * <p>
 */
public final class ReentrantReadWriteLockUsageMonitor implements AutoCloseable {

  public static final int DEFAULT_SAMPLING_INTERVAL_MS =
    SystemProperties.getIntProperty("ReentrantReadWriteLockUsageMonitor.DEFAULT_SAMPLING_INTERVAL_MS", 500);

  private final @NotNull Supplier<? extends ReentrantReadWriteLock> lockToMonitor;

  private final ScheduledFuture<?> scheduledSamplerHandle;

  private final ObservableDoubleMeasurement competingThreadsAvg;
  private final ObservableLongMeasurement competingThreads90P;
  private final ObservableLongMeasurement competingThreadsMax;
  private final BatchCallback meterHandle;

  //@GuardedBy(this)
  private final Histogram competingThreadsHisto = new Histogram(3);

  public ReentrantReadWriteLockUsageMonitor(final @NotNull ReentrantReadWriteLock toMonitor,
                                            final @NotNull String measurementName,
                                            final @NotNull Meter otelMeter) {
    this(() -> toMonitor, measurementName, otelMeter);
  }

  public ReentrantReadWriteLockUsageMonitor(final @NotNull Supplier<? extends ReentrantReadWriteLock> toMonitor,
                                            final @NotNull String measurementName,
                                            final @NotNull Meter otelMeter) {
    lockToMonitor = toMonitor;

    scheduledSamplerHandle = AppExecutorUtil.getAppScheduledExecutorService()
      .scheduleWithFixedDelay(this::sampleLockUsage, DEFAULT_SAMPLING_INTERVAL_MS, DEFAULT_SAMPLING_INTERVAL_MS, MILLISECONDS);

    competingThreadsAvg = otelMeter.gaugeBuilder(measurementName + ".competingThreads.avg").buildObserver();
    competingThreads90P = otelMeter.gaugeBuilder(measurementName + ".competingThreads.90P").ofLongs().buildObserver();
    competingThreadsMax = otelMeter.gaugeBuilder(measurementName + ".competingThreads.max").ofLongs().buildObserver();

    meterHandle = otelMeter.batchCallback(this::drainValuesToOtel, competingThreadsAvg, competingThreads90P, competingThreadsMax);
  }

  private synchronized void sampleLockUsage() {
    final ReentrantReadWriteLock lock = lockToMonitor.get();
    if (lock != null) {
      final boolean isWriteLocked = lock.isWriteLocked();
      final int queueLength = lock.getQueueLength();

      final int competingThreads = queueLength + (isWriteLocked ? 1 : lock.getReadLockCount());
      competingThreadsHisto.recordValue(competingThreads);
    }
  }

  private synchronized void drainValuesToOtel() {
    if (competingThreadsHisto.getTotalCount() > 0) {
      competingThreadsAvg.record(competingThreadsHisto.getMean());
      competingThreads90P.record(competingThreadsHisto.getValueAtPercentile(90));
      competingThreadsMax.record(competingThreadsHisto.getMaxValue());

      competingThreadsHisto.reset();
    }
    else {
      competingThreadsAvg.record(0);
      competingThreads90P.record(0);
      competingThreadsMax.record(0);
    }
  }

  @Override
  public void close() throws Exception {
    scheduledSamplerHandle.cancel(false);
    meterHandle.close();
  }
}
