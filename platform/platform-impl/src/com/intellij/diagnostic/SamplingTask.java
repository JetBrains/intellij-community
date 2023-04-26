// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.sun.management.OperatingSystemMXBean;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.NotNull;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

class SamplingTask {
  private final static ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();
  private final static List<GarbageCollectorMXBean> GC_MX_BEANS = ManagementFactory.getGarbageCollectorMXBeans();

  private final int myDumpInterval;
  private final int myMaxDumps;

  private final List<ThreadInfo[]> myThreadInfos = new ArrayList<>();

  private final Job job;

  private final long myStartTime;
  private long myCurrentTime;
  private final long myGcStartTime;
  private long myGcCurrentTime;
  private final double myProcessCpuLoad;

  SamplingTask(int intervalMs, int maxDurationMs) {
    myDumpInterval = intervalMs;
    myMaxDumps = maxDurationMs / intervalMs;
    myCurrentTime = myStartTime = System.nanoTime();
    myGcCurrentTime = myGcStartTime = currentGcTime();
    myProcessCpuLoad = ((OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean()).getProcessCpuLoad();
    job = PerformanceWatcher.getInstance().scheduleWithFixedDelay(this::dumpThreads, myDumpInterval);
  }

  private void dumpThreads() {
    myCurrentTime = System.nanoTime();
    myGcCurrentTime = currentGcTime();
    ThreadInfo[] infos = ThreadDumper.getThreadInfos(THREAD_MX_BEAN, false);
    if (!job.isCancelled()) {
      myThreadInfos.add(infos);
      if (myThreadInfos.size() >= myMaxDumps) {
        stop();
      }
      dumpedThreads(ThreadDumper.getThreadDumpInfo(infos, true));
    }
  }

  protected void dumpedThreads(@NotNull ThreadDump threadDump) {
  }

  private static long currentGcTime() {
    return GC_MX_BEANS.stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
  }

  public final int getDumpInterval() {
    return myDumpInterval;
  }

  public final List<ThreadInfo[]> getThreadInfos() {
    return myThreadInfos;
  }

  public final long getTotalTime() {
    return TimeUnit.NANOSECONDS.toMillis(myCurrentTime - myStartTime);
  }

  public final long getGcTime() {
    return myGcCurrentTime - myGcStartTime;
  }

  public final double getProcessCpuLoad() {
    return myProcessCpuLoad;
  }

  public final boolean isValid(long dumpingDuration) {
    return myThreadInfos.size() >= Math.max(10, Math.min(myMaxDumps, dumpingDuration / myDumpInterval / 2));
  }

  public void stop() {
    job.cancel(null);
  }
}