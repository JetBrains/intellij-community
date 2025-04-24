// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.common.ThreadLeakTracker;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNull;

public class JVMResponsivenessMonitorTest {
  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  public void monitorThreadIsTerminatedAfterClose() throws InterruptedException {
    Assumptions.assumeFalse(UsefulTestCase.IS_UNDER_TEAMCITY);

    Map<String, Thread> threadByNameBefore = ThreadLeakTracker.getThreads();
    Thread monitorThreadBeforeStarted = threadByNameBefore.get(JVMResponsivenessMonitor.MONITOR_THREAD_NAME);
    assertNull(
      monitorThreadBeforeStarted,
      () -> "It must be no monitor's sampling thread before monitor is started! but: " + monitorThreadBeforeStarted + ", alive: " + monitorThreadBeforeStarted.isAlive()
    );
    
    JVMResponsivenessMonitor monitor = new JVMResponsivenessMonitor();
    monitor.close();

    Map<String, Thread> threadByName = ThreadLeakTracker.getThreads();
    Thread monitorThread = threadByName.get(JVMResponsivenessMonitor.MONITOR_THREAD_NAME);
    assertNull(
      monitorThread,
      () -> "Monitor's sampling thread must be terminated after .close(), but: " + monitorThread + ", alive: " + monitorThread.isAlive()
    );
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  public void threadLeakTrackerIgnoresResponsivenessMonitorThread() throws InterruptedException {
    Assumptions.assumeFalse(UsefulTestCase.IS_UNDER_TEAMCITY);

    Map<String, Thread> threadsBefore = ThreadLeakTracker.getThreads();
    try (@SuppressWarnings("unused") JVMResponsivenessMonitor monitor = new JVMResponsivenessMonitor()) {
      ThreadLeakTracker.checkLeak(threadsBefore);
    }
  }
}
