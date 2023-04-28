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

  //How long (in nanoseconds) task waits in FlushQueue before starting execution (avg/90%/max):
  private final ObservableDoubleMeasurement waitingTimeAvgNs;
  private final ObservableLongMeasurement waitingTime90PNs;
  private final ObservableLongMeasurement waitingTimeMaxNs;

  //How many items was in FlushQueue at the moment next task is enqueued (avg/90%/max):
  private final ObservableDoubleMeasurement queueSizeAvg;
  private final ObservableLongMeasurement queueSize90P;
  private final ObservableLongMeasurement queueSizeMax;

  //How long (in nanoseconds) task executes (avg/90%/max):
  private final ObservableDoubleMeasurement executionTimeAvgNs;
  private final ObservableLongMeasurement executionTime90PNs;
  private final ObservableLongMeasurement executionTimeMaxNs;

  // ========== AWT EventQueue measurements:

  /**
   * How many events was processed by AWT EventQueue ({@linkplain EventQueue#dispatchEvent(AWTEvent)})
   */
  private final ObservableLongMeasurement awtEventsDispatchedCounter;

  //How long (in nanoseconds) does it take to process AWT events in (Ide)EventQueue.dispatchEvent(e)
  // (avg/90%/max):
  private final ObservableDoubleMeasurement awtDispatchTimeAvgNs;
  private final ObservableLongMeasurement awtDispatchTime90PNs;
  private final ObservableLongMeasurement awtDispatchTimeMaxNs;


  private final BatchCallback batchCallback;


  public OtelReportingEventWatcher() {
    this(TraceManager.INSTANCE.getMeter("EDT"));
  }

  public OtelReportingEventWatcher(final @NotNull Meter meter) {
    otelMeter = meter;

    //RC: by the nature this should be 'counter', not 'gauge' (since it is additive metrics), but counters have to
    //    be reported as cumulative sum, while with current data collecting approach it is much more natural to report increments
    tasksExecutedCounter = otelMeter.gaugeBuilder("FlushQueue.tasksExecuted").ofLongs().buildObserver();

    waitingTimeAvgNs = otelMeter.gaugeBuilder("FlushQueue.waitingTimeAvgNs").setUnit("ns").buildObserver();
    waitingTime90PNs = otelMeter.gaugeBuilder("FlushQueue.waitingTime90PNs").setUnit("ns").ofLongs().buildObserver();
    waitingTimeMaxNs = otelMeter.gaugeBuilder("FlushQueue.waitingTimeMaxNs").setUnit("ns").ofLongs().buildObserver();

    executionTimeAvgNs = otelMeter.gaugeBuilder("FlushQueue.executionTimeAvgNs").setUnit("ns").buildObserver();
    executionTime90PNs = otelMeter.gaugeBuilder("FlushQueue.executionTime90PNs").setUnit("ns").ofLongs().buildObserver();
    executionTimeMaxNs = otelMeter.gaugeBuilder("FlushQueue.executionTimeMaxNs").setUnit("ns").ofLongs().buildObserver();

    queueSizeAvg = otelMeter.gaugeBuilder("FlushQueue.queueSizeAvg").buildObserver();
    queueSize90P = otelMeter.gaugeBuilder("FlushQueue.queueSize90P").ofLongs().buildObserver();
    queueSizeMax = otelMeter.gaugeBuilder("FlushQueue.queueSizeMax").ofLongs().buildObserver();

    //RC: by the nature this should be 'counter', not 'gauge' (since it is additive metrics), but counters have to
    //    be reported as cumulative sum, while with current data collecting approach it is much more natural to report increments
    awtEventsDispatchedCounter = otelMeter.gaugeBuilder("AWTEventQueue.eventsDispatched").ofLongs().buildObserver();

    awtDispatchTimeAvgNs = otelMeter.gaugeBuilder("AWTEventQueue.dispatchTimeAvgNs").setUnit("ns").buildObserver();
    awtDispatchTime90PNs = otelMeter.gaugeBuilder("AWTEventQueue.dispatchTime90PNs").setUnit("ns").ofLongs().buildObserver();
    awtDispatchTimeMaxNs = otelMeter.gaugeBuilder("AWTEventQueue.dispatchTimeMaxNs").setUnit("ns").ofLongs().buildObserver();

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
      waitingTimeAvgNs, waitingTime90PNs, waitingTimeMaxNs,
      queueSizeAvg, queueSize90P, queueSizeMax,
      executionTimeAvgNs, executionTime90PNs, executionTimeMaxNs,
      awtEventsDispatchedCounter,
      awtDispatchTimeAvgNs, awtDispatchTime90PNs, awtDispatchTimeMaxNs
    );
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

    waitingTimeAvgNs.record(intervalWaitingTimes.getMean());
    waitingTime90PNs.record(intervalWaitingTimes.getValueAtPercentile(90));
    waitingTimeMaxNs.record(intervalWaitingTimes.getMaxValue());

    queueSizeAvg.record(intervalQueueSizes.getMean());
    queueSize90P.record(intervalQueueSizes.getValueAtPercentile(90));
    queueSizeMax.record(intervalQueueSizes.getMaxValue());

    executionTimeAvgNs.record(intervalExecutionTimes.getMean());
    executionTime90PNs.record(intervalExecutionTimes.getValueAtPercentile(90));
    executionTimeMaxNs.record(intervalExecutionTimes.getMaxValue());


    awtEventsDispatchedCounter.record(intervalAWTDispatchTimes.getTotalCount());

    awtDispatchTimeAvgNs.record(intervalAWTDispatchTimes.getMean());
    awtDispatchTime90PNs.record(intervalAWTDispatchTimes.getValueAtPercentile(90));
    awtDispatchTimeMaxNs.record(intervalAWTDispatchTimes.getMaxValue());
  }
}
