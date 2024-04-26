// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.testFramework.common.ThreadLeakTracker;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;

public class JVMResponsivenessMonitorTest {
  @Test
  @Timeout(5000)
  @Disabled("Test fails on TeamCity because JVMResponsivenessMonitor's thread is left running from some other test")
  public void monitorThreadIsTerminatedAfterClose() throws InterruptedException {
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
  @Timeout(5000)
  public void threadLeakTrackerIgnoresResponsivenessMonitorThread() throws InterruptedException {
    Map<String, Thread> threadsBefore = ThreadLeakTracker.getThreads();
    try (JVMResponsivenessMonitor monitor = new JVMResponsivenessMonitor()) {
      ThreadLeakTracker.checkLeak(threadsBefore);
    }
  }
}