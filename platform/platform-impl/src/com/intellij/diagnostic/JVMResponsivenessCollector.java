// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import org.HdrHistogram.Histogram;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import static com.intellij.util.SystemProperties.getIntProperty;

/**
 * Follows idea of <a href="https://github.com/giltene/jHiccup">jhiccup</a> tool: monitors
 * general JVM responsiveness by periodically run a sample CPU/memory-bound task, measure
 * its run time, creates a histogram over task run distribution, and report a few chosen
 * statistics (avg, 50-99-99.99%...) to FUS, as 'system.responsiveness:responsiveness' event
 * <p>
 * The goal is to estimate how many 'hiccups' -- tail-delays in ordinary CPU/memory-bound
 * task execution time, whose reasons are (likely) outside of our control -- IDE JVM experiences,
 * in a long run.
 * <p>
 * Collects the JVM responsiveness data from {@link JVMResponsivenessMonitor}, and reports
 * them to FUS.
 */
class JVMResponsivenessCollector extends ApplicationUsagesCollector implements Disposable {
  private static final Logger LOG = Logger.getInstance(JVMResponsivenessCollector.class);

  // The difference with the original jhiccup: original jhiccup aims to gauge the OS _thread
  // scheduling_ tail-latencies, -- i.e. how (un)likely it is for a thread to be scheduled on
  // a CPU way later than it should be in a perfect world.
  // This class aims to gauge the same kind of tail-latencies, but for the _task-execution_ time,
  // for CPU & memory-bound task.
  // This task-execution time is affected by:
  //  1. OS thread scheduling: thread could be scheduled off CPU during the execution -- because it's
  //     time slice is finished. The time until the thread will be scheduled back on-CPU depends on
  //     overall system load -- number of active threads, interrupts, thread packing/unparking, etc
  //  2. Memory pressure: memory capacity vs memory demands. Intensive memory use by other apps in
  //     the system leads to swapping, but also to CPU cache 'trashing' -- both significantly slow
  //     down memory access

  private static final int SAMPLING_PERIOD_MS = getIntProperty("JVMResponsivenessCollector.SAMPLING_PERIOD_MS", 500);
  private static final int MEMORY_BUFFER_SIZE = getIntProperty("JVMResponsivenessCollector.MEMORY_BUFFER_SIZE", 1024 * 1024);
  private static final int MEMORY_OPS_PER_RUN = getIntProperty("JVMResponsivenessCollector.MEMORY_OPS_PER_RUN", 1_000);


  private final static EventLogGroup GROUP = new EventLogGroup("system.responsiveness", 1);

  private static final LongEventField AVG_NS = new LongEventField("avg_ns");
  private static final LongEventField P50_NS = new LongEventField("p50_ns");

  //below fields are _relative_: 99%/50%, 99.99%/50%, max/50%
  private static final DoubleEventField P99_P50 = new DoubleEventField("p99_p50");
  private static final DoubleEventField P9999_P50 = new DoubleEventField("p9999_p50");
  private static final DoubleEventField MAX_P50 = new DoubleEventField("max_p50");

  private final static VarargEventId RESPONSIVENESS_EVENT = GROUP.registerVarargEvent(
    "responsiveness",
    AVG_NS, P50_NS, P99_P50, P9999_P50, MAX_P50
  );

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  private final JVMResponsivenessMonitor monitor = new JVMResponsivenessMonitor();

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics() {
    Set<MetricEvent> metrics = new HashSet<>();
    monitor.read(histogram -> {
      double avg_ns = histogram.getMean();
      long p50_ns = histogram.getValueAtPercentile(50);
      long p99_ns = histogram.getValueAtPercentile(99);
      long p9999_ns = histogram.getValueAtPercentile(99.99);
      long max_ns = histogram.getMaxValue();

      metrics.add(
        RESPONSIVENESS_EVENT.metric(
          AVG_NS.with((long)avg_ns),
          P50_NS.with(p50_ns),
          P99_P50.with(p99_ns * 1.0 / p50_ns),
          P9999_P50.with(p9999_ns * 1.0 / p50_ns),
          MAX_P50.with(max_ns * 1.0 / p50_ns)
        )
      );


      //if(LOG.isDebugEnabled()){
      LOG.info("JVM responsiveness: {avg: " + avg_ns + ", median: " + p50_ns + ", 99%: " + p99_ns + ", 99.99%: " + p9999_ns + "}ns");
      //}
    }, /*reset: */ true);

    return metrics;
  }

  @Override
  public void dispose() {
    try {
      monitor.close();
    }
    catch (Throwable e) {
      LOG.error("Error closing monitor", e);
    }
  }

  private static final class JVMResponsivenessMonitor implements AutoCloseable {

    private final Thread samplingThread = new Thread(this::samplingLoop, "JVMResponsivenessMonitor");

    private JVMResponsivenessMonitor() {
      samplingThread.setDaemon(true);
      samplingThread.start();
    }

    /** task duration in nanoseconds */
    //@GuardedBy(taskDurationHisto)
    private final Histogram taskDurationHisto = new Histogram(3);

    private void samplingLoop() {
      while (!Thread.currentThread().isInterrupted()) {

        long timeTakenNs = runCpuAndMemoryTask();

        synchronized (taskDurationHisto) {
          taskDurationHisto.recordValue((int)timeTakenNs);
        }

        try {
          //noinspection BusyWait
          Thread.sleep(SAMPLING_PERIOD_MS);
        }
        catch (InterruptedException e) {
          LOG.info("Sampling thread interrupted -> stop sampling", e);
          return;
        }
      }
      LOG.info("Sampling thread exiting");
    }

    public void read(@NotNull Consumer<Histogram> consumer,
                     boolean resetHistogram) {
      synchronized (taskDurationHisto) {
        consumer.accept(taskDurationHisto);
        if (resetHistogram) {
          taskDurationHisto.reset();
        }
      }
    }

    private final byte[] heapArrayToSample = new byte[MEMORY_BUFFER_SIZE];

    private long runCpuAndMemoryTask() {
      ThreadLocalRandom rnd = ThreadLocalRandom.current();
      long startedAtNs = System.nanoTime();
      long accumulator = 1;
      //the task: make N random-position reads & writes from/to an array
      for (int i = 0; i < MEMORY_OPS_PER_RUN; i++) {
        int index = rnd.nextInt(heapArrayToSample.length);
        byte value = heapArrayToSample[index];
        accumulator += value;
        heapArrayToSample[index] = (byte)accumulator;
      }
      return System.nanoTime() - startedAtNs;
    }

    @Override
    public void close() throws Exception {
      samplingThread.interrupt();
      samplingThread.join();
    }
  }
}
