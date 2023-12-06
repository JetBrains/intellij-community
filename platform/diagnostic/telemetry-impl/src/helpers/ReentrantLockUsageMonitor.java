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
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Monitors given {@link ReentrantLock} usage: regularly samples the lock, and calculates utilization metrics:
 * 'competingThreads' = {@link ReentrantLock#getQueueLength()} (threads waiting for acquire the lock) + 1 if
 * lock is currently in use ({@link ReentrantLock#isLocked()}).
 * Drains this 'competingThreads' avg/90%/max as OTel metrics.
 * <p>
 * <p>
 * Measurements reported as {@code [measurementName + '.competingThreads.(avg|90P|max)']}
 */
public final class ReentrantLockUsageMonitor implements AutoCloseable {

  public static final int DEFAULT_SAMPLING_INTERVAL_MS =
    SystemProperties.getIntProperty("ReentrantLockUsageMonitor.DEFAULT_SAMPLING_INTERVAL_MS", 500);

  private final Supplier<ReentrantLock> lockToMonitor;

  private final ScheduledFuture<?> scheduledSamplerHandle;

  private final ObservableDoubleMeasurement competingThreadsAvg;
  private final ObservableLongMeasurement competingThreads90P;
  private final ObservableLongMeasurement competingThreadsMax;
  private final BatchCallback meterHandle;

  //@GuardedBy(this)
  private final Histogram competingThreadsHisto = new Histogram(3);

  public ReentrantLockUsageMonitor(final @NotNull ReentrantLock toMonitor,
                                   final @NotNull String measurementName,
                                   final @NotNull Meter otelMeter) {
    this(() -> toMonitor, measurementName, otelMeter);
  }

  public ReentrantLockUsageMonitor(final @NotNull Supplier<ReentrantLock> toMonitor,
                                   final @NotNull String measurementName,
                                   final @NotNull Meter otelMeter) {
    lockToMonitor = toMonitor;

    scheduledSamplerHandle = AppExecutorUtil
      .getAppScheduledExecutorService()
      .scheduleWithFixedDelay(this::sampleLockUsage, DEFAULT_SAMPLING_INTERVAL_MS, DEFAULT_SAMPLING_INTERVAL_MS, MILLISECONDS);

    competingThreadsAvg = otelMeter.gaugeBuilder(measurementName + ".competingThreads.avg").buildObserver();
    competingThreads90P = otelMeter.gaugeBuilder(measurementName + ".competingThreads.90P").ofLongs().buildObserver();
    competingThreadsMax = otelMeter.gaugeBuilder(measurementName + ".competingThreads.max").ofLongs().buildObserver();

    meterHandle = otelMeter.batchCallback(this::drainValuesToOtel, competingThreadsAvg, competingThreads90P, competingThreadsMax);
  }

  private synchronized void sampleLockUsage() {
    final ReentrantLock lock = lockToMonitor.get();
    if (lock != null) {
      final boolean isLocked = lock.isLocked();
      final int queueLength = lock.getQueueLength();
      final int competingThreads = queueLength + (isLocked ? 1 : 0);
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
