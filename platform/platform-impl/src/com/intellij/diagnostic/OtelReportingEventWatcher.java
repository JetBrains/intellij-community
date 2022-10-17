// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.diagnostic.telemetry.TraceManager;
import com.intellij.openapi.Disposable;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.SingleWriterRecorder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static java.util.concurrent.TimeUnit.HOURS;

/**
 * Gather stats about {@linkplain com.intellij.openapi.application.impl.FlushQueue} tasks and {@linkplain EventQueue} events
 * (timings, queue length), and report the stats as OpenTelemetry metrics
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public class OtelReportingEventWatcher implements EventWatcher, Disposable {
  private final @NotNull Meter otelMeter;


  private final SingleWriterRecorder waitingTimesHistogram = new SingleWriterRecorder(2);
  private final SingleWriterRecorder queueSizesHistogram = new SingleWriterRecorder(2);
  private final SingleWriterRecorder executionTimeHistogram = new SingleWriterRecorder(2);
  private final SingleWriterRecorder awtEventDispatchTimeHistogram = new SingleWriterRecorder(2);

  // ========== cached histograms copies (for thread-safe reading without interrupting writing)
  //@GuardedBy("this")
  private Histogram intervalWaitingTimes = null;
  //@GuardedBy("this")
  private Histogram intervalQueueSizes = null;
  //@GuardedBy("this")
  private Histogram intervalExecutionTimes = null;
  //@GuardedBy("this")
  private Histogram intervalAWTDispatchTimes = null;

  // ========== FlushQueue measurements:

  /**
   * How many tasks have gone through FlushQueue
   */
  private final ObservableLongMeasurement tasksExecutedCounter;

  private final ObservableDoubleMeasurement waitingTimeAvg;
  private final ObservableLongMeasurement waitingTime90P;
  private final ObservableLongMeasurement waitingTimeMax;

  private final ObservableDoubleMeasurement queueSizeAvg;
  private final ObservableLongMeasurement queueSize90P;
  private final ObservableLongMeasurement queueSizeMax;

  private final ObservableDoubleMeasurement executionTimeAvg;
  private final ObservableLongMeasurement executionTime90P;
  private final ObservableLongMeasurement executionTimeMax;

  // ========== AWT EventQueue measurements:

  /**
   * How many events was processed by AWT EventQueue ({@linkplain EventQueue#dispatchEvent(AWTEvent)})
   */
  private final ObservableLongMeasurement awtEventsDispatchedCounter;

  //How long does it take to process AWT events in (Ide)EventQueue.dispatchEvent(e)
  private final ObservableDoubleMeasurement awtDispatchTimeAvg;
  private final ObservableLongMeasurement awtDispatchTime90P;
  private final ObservableLongMeasurement awtDispatchTimeMax;


  private final BatchCallback batchCallback;


  public OtelReportingEventWatcher() {
    this(TraceManager.INSTANCE.getMeter("EDT"));
  }

  public OtelReportingEventWatcher(final @NotNull Meter meter) {
    otelMeter = meter;

    //RC: by the nature this should be 'counter', not 'gauge' (since it is additive metrics), but counters have to
    //    be reported as cumulative sum, while with current data collecting approach it is much more natural to report increments
    tasksExecutedCounter = otelMeter.gaugeBuilder("FlushQueue.tasksExecuted").ofLongs().buildObserver();

    waitingTimeAvg = otelMeter.gaugeBuilder("FlushQueue.waitingTimeAvg").buildObserver();
    waitingTime90P = otelMeter.gaugeBuilder("FlushQueue.waitingTime90P").ofLongs().buildObserver();
    waitingTimeMax = otelMeter.gaugeBuilder("FlushQueue.waitingTimeMax").ofLongs().buildObserver();

    executionTimeAvg = otelMeter.gaugeBuilder("FlushQueue.executionTimeAvg").buildObserver();
    executionTime90P = otelMeter.gaugeBuilder("FlushQueue.executionTime90P").ofLongs().buildObserver();
    executionTimeMax = otelMeter.gaugeBuilder("FlushQueue.executionTimeMax").ofLongs().buildObserver();

    queueSizeAvg = otelMeter.gaugeBuilder("FlushQueue.queueSizeAvg").buildObserver();
    queueSize90P = otelMeter.gaugeBuilder("FlushQueue.queueSize90P").ofLongs().buildObserver();
    queueSizeMax = otelMeter.gaugeBuilder("FlushQueue.queueSizeMax").ofLongs().buildObserver();

    //RC: by the nature this should be 'counter', not 'gauge' (since it is additive metrics), but counters have to
    //    be reported as cumulative sum, while with current data collecting approach it is much more natural to report increments
    awtEventsDispatchedCounter = otelMeter.gaugeBuilder("AWTEventQueue.eventsDispatched").ofLongs().buildObserver();

    awtDispatchTimeAvg = otelMeter.gaugeBuilder("AWTEventQueue.dispatchTimeAvg").buildObserver();
    awtDispatchTime90P = otelMeter.gaugeBuilder("AWTEventQueue.dispatchTime90P").ofLongs().buildObserver();
    awtDispatchTimeMax = otelMeter.gaugeBuilder("AWTEventQueue.dispatchTimeMax").ofLongs().buildObserver();

    //MAYBE RC: 1 minute (default batchCallback period) is quite coarse scale, it averages a lot, and short spikes of waiting
    //     time could sink in noise on that scale. But it generates small amount of data, and could be always-on.
    //     We could try to report metrics more frequently (say, each second), with push-style api, with own executor. This
    //     could be more useful to identify short-timescale UI responsiveness spikes, but at the cost of much more data.
    //     Also push-style measurements in OTel are limited -- only counters (i.e. additive) metrics could be reported in a
    //     push-style way, i.e. gauges are only async (pull).
    //     ...From the other side, MAX aggregate makes visible even shortest spikes of latency, even though not allow to
    //     see precise time of it -- so maybe this is enough.

    batchCallback = meter.batchCallback(
      this::reportStatsForPeriod,
      tasksExecutedCounter,
      waitingTimeAvg, waitingTime90P, waitingTimeMax,
      queueSizeAvg, queueSize90P, queueSizeMax,
      executionTimeAvg, executionTime90P, executionTimeMax,
      awtEventsDispatchedCounter,
      awtDispatchTimeAvg, awtDispatchTime90P, awtDispatchTimeMax
    );
  }

  @Override
  public void runnableTaskFinished(final @NotNull Runnable runnable,
                                   final long waitedInQueueNs,
                                   final int queueSize,
                                   final long executionDurationNs) {
    waitingTimesHistogram.recordValue(waitedInQueueNs);
    queueSizesHistogram.recordValue(queueSize);
    executionTimeHistogram.recordValue(executionDurationNs);
  }


  private long awtEventExecutionStartedNs = -1;

  @RequiresEdt
  @Override
  public void edtEventStarted(final @NotNull AWTEvent event,
                              final long startedAtMs) {
    this.awtEventExecutionStartedNs = System.nanoTime();
  }

  @Override
  public void edtEventFinished(final @NotNull AWTEvent event,
                               final long finishedAtMs) {
    if (this.awtEventExecutionStartedNs <= 0) {
      return;// missed call to .edtEventStarted()
    }
    final long awtEventExecutionDurationNs = System.nanoTime() - this.awtEventExecutionStartedNs;
    if (awtEventExecutionDurationNs > HOURS.toNanos(1)) {
      return;// _likely_ missed call to .edtEventStarted()
    }
    awtEventDispatchTimeHistogram.recordValue(awtEventExecutionDurationNs);
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
    batchCallback.close();
  }

  private synchronized void reportStatsForPeriod() {
    //RC: this method should be called from myExecutor (single) thread only, hence synchronization here
    //    is only to be sure

    intervalWaitingTimes = waitingTimesHistogram.getIntervalHistogram(intervalWaitingTimes);
    intervalQueueSizes = queueSizesHistogram.getIntervalHistogram(intervalQueueSizes);
    intervalExecutionTimes = executionTimeHistogram.getIntervalHistogram(intervalExecutionTimes);
    intervalAWTDispatchTimes = awtEventDispatchTimeHistogram.getIntervalHistogram(intervalAWTDispatchTimes);

    tasksExecutedCounter.record(intervalWaitingTimes.getTotalCount());

    waitingTimeAvg.record(intervalWaitingTimes.getMean());
    waitingTime90P.record(intervalWaitingTimes.getValueAtPercentile(90));
    waitingTimeMax.record(intervalWaitingTimes.getMaxValue());

    queueSizeAvg.record(intervalQueueSizes.getMean());
    queueSize90P.record(intervalQueueSizes.getValueAtPercentile(90));
    queueSizeMax.record(intervalQueueSizes.getMaxValue());

    executionTimeAvg.record(intervalExecutionTimes.getMean());
    executionTime90P.record(intervalExecutionTimes.getValueAtPercentile(90));
    executionTimeMax.record(intervalExecutionTimes.getMaxValue());


    awtEventsDispatchedCounter.record(intervalAWTDispatchTimes.getTotalCount());

    awtDispatchTimeAvg.record(intervalAWTDispatchTimes.getMean());
    awtDispatchTime90P.record(intervalAWTDispatchTimes.getValueAtPercentile(90));
    awtDispatchTimeMax.record(intervalAWTDispatchTimes.getMaxValue());
  }
}
