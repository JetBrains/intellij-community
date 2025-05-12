// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.Disposable;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import io.opentelemetry.api.metrics.*;
import org.HdrHistogram.Histogram;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.intellij.platform.diagnostic.telemetry.PlatformScopesKt.EDT;

/**
 * Gather stats about {@linkplain com.intellij.openapi.application.impl.FlushQueue} tasks and {@linkplain EventQueue} events
 * (timings, queue length), and report the stats as OpenTelemetry metrics
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public final class OtelReportingEventWatcher extends PreciseEventWatcher implements Disposable {

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

  //Sum of times spent on AWT
  private final LongCounter awtTotalTimeNs;

  private final BatchCallback batchCallback;

  public OtelReportingEventWatcher() {
    this(TelemetryManager.getInstance().getMeter(EDT));
  }

  public OtelReportingEventWatcher(@NotNull Meter meter) {
    //RC: by the nature this should be 'counter', not 'gauge' (since it is additive metrics), but counters have to
    //    be reported as cumulative sum, while with current data collecting approach it is much more natural to report increments
    tasksExecutedCounter = meter.gaugeBuilder("FlushQueue.tasksExecuted").ofLongs().buildObserver();

    waitingTimeAvgNs = meter.gaugeBuilder("FlushQueue.waitingTimeAvgNs").setUnit("ns").buildObserver();
    waitingTime90PNs = meter.gaugeBuilder("FlushQueue.waitingTime90PNs").setUnit("ns").ofLongs().buildObserver();
    waitingTimeMaxNs = meter.gaugeBuilder("FlushQueue.waitingTimeMaxNs").setUnit("ns").ofLongs().buildObserver();

    executionTimeAvgNs = meter.gaugeBuilder("FlushQueue.executionTimeAvgNs").setUnit("ns").buildObserver();
    executionTime90PNs = meter.gaugeBuilder("FlushQueue.executionTime90PNs").setUnit("ns").ofLongs().buildObserver();
    executionTimeMaxNs = meter.gaugeBuilder("FlushQueue.executionTimeMaxNs").setUnit("ns").ofLongs().buildObserver();

    queueSizeAvg = meter.gaugeBuilder("FlushQueue.queueSizeAvg").buildObserver();
    queueSize90P = meter.gaugeBuilder("FlushQueue.queueSize90P").ofLongs().buildObserver();
    queueSizeMax = meter.gaugeBuilder("FlushQueue.queueSizeMax").ofLongs().buildObserver();

    //RC: by the nature this should be 'counter', not 'gauge' (since it is additive metrics), but counters have to
    //    be reported as cumulative sum, while with current data collecting approach it is much more natural to report increments
    awtEventsDispatchedCounter = meter.gaugeBuilder("AWTEventQueue.eventsDispatched").ofLongs().buildObserver();

    awtDispatchTimeAvgNs = meter.gaugeBuilder("AWTEventQueue.dispatchTimeAvgNs").setUnit("ns").buildObserver();
    awtDispatchTime90PNs = meter.gaugeBuilder("AWTEventQueue.dispatchTime90PNs").setUnit("ns").ofLongs().buildObserver();
    awtDispatchTimeMaxNs = meter.gaugeBuilder("AWTEventQueue.dispatchTimeMaxNs").setUnit("ns").ofLongs().buildObserver();

    //TODO: this metrics could be calculated as AWTEventQueue.dispatchTimeAvgNs * .eventsDispatched.
    //      it is only needed because startup performance benchmarking needed it
    awtTotalTimeNs = meter.counterBuilder("AWTEventQueue.dispatchTimeTotalNS").setUnit("ns").build();
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
  protected void edtEventFinishedExt(@NotNull AWTEvent event, long executionDurationNs) {
    awtTotalTimeNs.add(executionDurationNs);
  }

  @Override
  public void dispose() {
    batchCallback.close();
  }

  private synchronized void reportStatsForPeriod() {
    //RC: this method should be called from myExecutor (single) thread only, hence synchronization here
    //    is only to be sure

    Histogram intervalWaitingTimes = getWaitingTimeHistogram();
    Histogram intervalQueueSizes = getQueueSizeHistogram();
    Histogram intervalExecutionTimes = getExecutionTimeHistogram();
    Histogram intervalAWTDispatchTimes = getAWTEventDispatchTimeHistogram();

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
