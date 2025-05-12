// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.MathUtil;
import com.intellij.util.SlowOperations;
import org.HdrHistogram.Histogram;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import static com.intellij.diagnostic.UILatencyLogger.SLOW_OPERATIONS_ISSUES;
import static com.intellij.util.SystemProperties.getIntProperty;

/**
 * Follows an idea of <a href="https://github.com/giltene/jHiccup">jhiccup</a> tool: monitors
 * general JVM responsiveness by periodically run a sample CPU/memory-bound task and measure
 * its run time.
 * Creates a histogram over task run duration distribution, and reports a few chosen statistics
 * (avg, max, 50-99-99.9%-tile...) to FUS, as 'performance:responsiveness' event
 * <p>
 * The goal is to estimate how many 'hiccups' -- tail-delays in ordinary CPU/memory-bound
 * task execution time, whose reasons are (likely) outside of our control -- IDE JVM experiences,
 * in a long run.
 *
 * @see IdeHeartbeatEventReporter
 */
@ApiStatus.Internal
@Service
public final class JVMResponsivenessMonitor implements Disposable, AutoCloseable {
  private static final Logger LOG = Logger.getInstance(JVMResponsivenessMonitor.class);

  // The difference with the original jhiccup: original jhiccup aims to gauge the OS _thread
  // scheduling_ tail-latencies, -- i.e. how (un)likely it is for a thread to be scheduled on
  // a CPU way later than it should be in a perfect world.
  // This class aims to gauge the same kind of tail-latencies, but for the _task-execution_ time,
  // given the task is CPU & memory-bound only.
  // This task-execution time is affected by:
  //  1. OS thread scheduling: thread could be scheduled off CPU during the execution -- because it's
  //     time slice is finished. The time until the thread will be scheduled back on-CPU depends on
  //     overall system load -- number of active threads, interrupts, thread packing/unparking, etc
  //  2. Memory pressure: memory capacity vs memory demands. Intensive memory use by IDE and other
  //     apps in the system leads to swapping, but also to CPU cache 'trashing' -- both significantly
  //     slow down memory accesses
  //  3. GC pauses: probably, the most important for JVM threads, and a something we have at least
  //     some means to deal with

  private static final int SAMPLING_PERIOD_MS = getIntProperty("JVMResponsivenessMonitor.SAMPLING_PERIOD_MS", 1_000);
  /** Should be >1000 for 99.9%-ile to make any sense */
  private static final int REPORTING_EACH_N_SAMPLES = getIntProperty("JVMResponsivenessMonitor.REPORTING_EACH_N_SAMPLES", 3600);
  private static final int MEMORY_BUFFER_SIZE = getIntProperty("JVMResponsivenessMonitor.MEMORY_BUFFER_SIZE", 512 * 1024);
  /**
   * Should be tuned so that task duration ~ 10-50 us. Not too long so it won't affect overall app's performance, not too
   * short -- so it's duration could be measured with enough precision
   */
  private static final int MEMORY_OPS_PER_RUN = getIntProperty("JVMResponsivenessMonitor.MEMORY_OPS_PER_RUN", 100);


  public static final String MONITOR_THREAD_NAME = "JVMResponsivenessMonitor";


  private final Thread samplingThread = new Thread(this::samplingLoop, MONITOR_THREAD_NAME);

  public JVMResponsivenessMonitor() {
    samplingThread.setDaemon(true);
    samplingThread.start();
  }

  @Override
  public void dispose() {
    //MAYBE RC: ThreadLeakTrackerExtension still flag this thread leaked -- even though it must be
    //          terminated by the container. Added the thread name to the ThreadLeakTracker.knownOffenders
    //          list for now, because can't trace the reason it is 'leaked'. Maybe return to this later,
    //          and reconsider
    try {
      close();
    }
    catch (Throwable e) {
      LOG.error("Error closing monitor", e);
    }
  }

  @Override
  public void close() throws InterruptedException {
    //RC: there is a race between samplingThread.start() in ctor and .interrupt() here -- if thread is not yet
    //    started, .interrupt() could be just wasted, and join() will wait forever.
    //    The loop below fixes the race:
    while(samplingThread.getState() != Thread.State.TERMINATED) {
      samplingThread.interrupt();
      samplingThread.join(1000);
    }
  }

  /** task durations, in nanoseconds */
  private final Histogram taskDurationHisto = new Histogram(3);

  private void samplingLoop() {
    UILatencyLogger.MemoryStatsSampler memorySampler = new UILatencyLogger.MemoryStatsSampler();
    try {
      for (int sampleNo = 0;
           !Thread.currentThread().isInterrupted();
           sampleNo++) {
        long timeTakenNs = runCpuAndMemoryTask();

        taskDurationHisto.recordValue(MathUtil.clamp(timeTakenNs, 0, Long.MAX_VALUE));

        memorySampler.sample();

        try {
          //noinspection BusyWait
          Thread.sleep(SAMPLING_PERIOD_MS);
        }
        catch (InterruptedException e) {
          LOG.info("Sampling thread interrupted -> stop sampling");
          return;
        }

        if (sampleNo % REPORTING_EACH_N_SAMPLES == REPORTING_EACH_N_SAMPLES - 1) {
          reportAccumulatedStats(taskDurationHisto);
          taskDurationHisto.reset();
          memorySampler.logToFus();

          logSlowOperations();
        }
      }
    }
    catch (Throwable e) {
      LOG.error("Sampling thread exiting because of error", e);
    }
    finally {
      memorySampler.close();
      LOG.info("Sampling thread exiting normally");
    }
  }

  private static void logSlowOperations() {
    var knownIssues = SlowOperations.reportKnownIssues();
    SLOW_OPERATIONS_ISSUES.log(new ArrayList<>(knownIssues));
  }

  private static void reportAccumulatedStats(@NotNull Histogram taskDurationHisto) {
    double avg_ns = taskDurationHisto.getMean();
    long p50_ns = taskDurationHisto.getValueAtPercentile(50);
    long p99_ns = taskDurationHisto.getValueAtPercentile(99);
    long p999_ns = taskDurationHisto.getValueAtPercentile(99.9);
    long max_ns = taskDurationHisto.getMaxValue();
    int measurementsCount = (int)taskDurationHisto.getTotalCount();

    UILatencyLogger.reportResponsiveness(avg_ns, p50_ns, p99_ns, p999_ns, max_ns, measurementsCount);

    LOG.info(
      "JVM responsiveness: {avg: " + avg_ns + ", 50%: " + p50_ns + ", " +
      "99%: " + p99_ns + ", 99.9%: " + p999_ns + ", max: " + max_ns + " }ns, x{" + measurementsCount + " measurements}"
    );
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
}
