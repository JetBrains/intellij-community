// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.Disposable;
import com.intellij.util.ui.EDT;
import kotlin.jvm.Volatile;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.SingleWriterRecorder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static java.util.concurrent.TimeUnit.HOURS;

/**
 * Gather stats about {@linkplain com.intellij.openapi.application.impl.FlushQueue} tasks and {@linkplain EventQueue} events
 * (timings, queue length)
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public abstract class PreciseEventWatcher implements EventWatcher, Disposable {
  private final SingleWriterRecorder waitingTimesHistogram = new SingleWriterRecorder(2);
  private final SingleWriterRecorder queueSizesHistogram = new SingleWriterRecorder(2);
  private final SingleWriterRecorder executionTimeHistogram = new SingleWriterRecorder(2);
  private final SingleWriterRecorder awtEventDispatchTimeHistogram = new SingleWriterRecorder(2);

  // ========== cached histograms copies (for thread-safe reading without interrupting writing)
  //@GuardedBy("this")
  @Volatile
  private Histogram intervalWaitingTimes = null;
  //@GuardedBy("this")
  @Volatile
  private Histogram intervalQueueSizes = null;
  //@GuardedBy("this")
  @Volatile
  private Histogram intervalExecutionTimes = null;
  //@GuardedBy("this")
  @Volatile
  private Histogram intervalAWTDispatchTimes = null;

  protected Histogram getWaitingTimeHistogram() {
    intervalWaitingTimes = waitingTimesHistogram.getIntervalHistogram(intervalWaitingTimes);
    return intervalWaitingTimes;
  }

  protected Histogram getQueueSizeHistogram() {
    intervalQueueSizes = queueSizesHistogram.getIntervalHistogram(intervalQueueSizes);
    return intervalQueueSizes;
  }

  protected Histogram getExecutionTimeHistogram() {
    intervalExecutionTimes = executionTimeHistogram.getIntervalHistogram(intervalExecutionTimes);
    return intervalExecutionTimes;
  }

  protected Histogram getAWTEventDispatchTimeHistogram() {
    intervalAWTDispatchTimes = awtEventDispatchTimeHistogram.getIntervalHistogram(intervalAWTDispatchTimes);
    return intervalAWTDispatchTimes;
  }

  @Override
  public void runnableTaskFinished(final @NotNull Runnable runnable,
                                   final long waitedInQueueNs,
                                   final int queueSize,
                                   final long executionDurationNs,
                                   final boolean wasInSkippedItems) {
    //wasInSkippedItems is true for tasks that couldn't be executed in order because of modalityState mismatch, and
    // was delayed. Such a delay is huge compared to usual task queue waiting times, and 'skipped' tasks dominate
    // waiting time stats -- which is undesirable, since we want waiting times to be a metric of queue utilization,
    // not modal dialogs opening times. What is why I excluded skippedItems from stats: this leads to a bias in
    // queue stats, but even with such a bias queue stats still somehow describes queue utilization -- while total
    // (non-skipped+skipped) stats describes mostly user behavior in relation to a modal dialogs.
    //FIXME RC: Better approach would be to gather wasInSkippedItems=true tasks statistics separately, because they
    //          naturally have much longer waiting times, better not to be mixed with regular (not bypassed) tasks
    //          waiting times. Also, it could be beneficial to collect for 'skipped' tasks their waiting times since
    //          re-appending to a queue (see apt TODOs in FlushQueue)
    if (!wasInSkippedItems) {
      waitingTimesHistogram.recordValue(waitedInQueueNs);
      queueSizesHistogram.recordValue(queueSize);
      executionTimeHistogram.recordValue(executionDurationNs);
    }
  }

  protected long awtEventExecutionStartedNs = -1;

  @Override
  public void edtEventStarted(@NotNull AWTEvent event, long startedAtMs) {
    EDT.assertIsEdt();
    this.awtEventExecutionStartedNs = System.nanoTime();
  }


  @Override
  public void edtEventFinished(final @NotNull AWTEvent event,
                               final long finishedAtMs) {
    if (this.awtEventExecutionStartedNs <= 0) {
      return;// missed call to .edtEventStarted()
    }
    final long awtEventExecutionDurationNs = System.nanoTime() - this.awtEventExecutionStartedNs;
    if (awtEventExecutionDurationNs > HOURS.toNanos(1) || awtEventExecutionDurationNs < 0) {
      return;// _likely_ missed call to .edtEventStarted()
    }
    awtEventDispatchTimeHistogram.recordValue(awtEventExecutionDurationNs);
    edtEventFinishedExt(event, awtEventExecutionDurationNs);
  }

  protected void edtEventFinishedExt(final @NotNull AWTEvent event, long executionDurationNs) {
  }

  @Override
  public void reset() {
    this.awtEventExecutionStartedNs = -1;
  }

  @Override
  public void logTimeMillis(final @NotNull String processId,
                            final long startedAtMs,
                            final @NotNull Class<? extends Runnable> runnableClass) {
    //nothing
  }

  @Override
  public void dispose() {
  }
}
