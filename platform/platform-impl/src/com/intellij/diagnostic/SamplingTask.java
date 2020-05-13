// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import java.lang.management.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SamplingTask {
  private final static ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();
  private final static OperatingSystemMXBean OS_MX_BEAN = ManagementFactory.getOperatingSystemMXBean();
  private final static List<GarbageCollectorMXBean> GC_MX_BEANS = ManagementFactory.getGarbageCollectorMXBeans();

  private final int myDumpInterval;
  private final int myMaxDumps;

  private final List<ThreadInfo[]> myThreadInfos = new ArrayList<>();

  private final ScheduledFuture<?> myFuture;

  private final long myStartTime;
  private long myCurrentTime;
  private final long myGcStartTime;
  private long myGcCurrentTime;
  private final double myOsAverageLoad;

  public SamplingTask(int intervalMs, int maxDurationMs) {
    myDumpInterval = intervalMs;
    myMaxDumps = maxDurationMs / intervalMs;
    myCurrentTime = myStartTime = System.nanoTime();
    myGcCurrentTime = myGcStartTime = currentGcTime();
    myOsAverageLoad = OS_MX_BEAN.getSystemLoadAverage();
    ScheduledExecutorService executor = PerformanceWatcher.getInstance().getExecutor();
    myFuture = executor.scheduleWithFixedDelay(this::dumpThreads, 0, myDumpInterval, TimeUnit.MILLISECONDS);
  }

  private void dumpThreads() {
    myCurrentTime = System.nanoTime();
    myGcCurrentTime = currentGcTime();
    ThreadInfo[] infos = ThreadDumper.getThreadInfos(THREAD_MX_BEAN, false);
    if (!myFuture.isCancelled()) {
      myThreadInfos.add(infos);
      if (myThreadInfos.size() >= myMaxDumps) {
        stop();
      }
      dumpedThreads(infos);
    }
  }

  protected void dumpedThreads(ThreadInfo[] infos) {
  }

  private static long currentGcTime() {
    return GC_MX_BEANS.stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
  }

  public int getDumpInterval() {
    return myDumpInterval;
  }

  public List<ThreadInfo[]> getThreadInfos() {
    return myThreadInfos;
  }

  public long getSampledTime() {
    return (long)myThreadInfos.size() * myDumpInterval;
  }

  public long getTotalTime() {
    return TimeUnit.NANOSECONDS.toMillis(myCurrentTime - myStartTime);
  }

  public long getGcTime() {
    return myGcCurrentTime - myGcStartTime;
  }

  public double getOsAverageLoad() {
    return myOsAverageLoad;
  }

  public boolean isValid(long dumpingDuration) {
    return myThreadInfos.size() >= Math.max(10, Math.min(myMaxDumps, dumpingDuration / myDumpInterval / 2));
  }

  public void stop() {
    myFuture.cancel(false);
  }
}