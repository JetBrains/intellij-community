// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class FreezeDetector {
  private static final Logger LOG = Logger.getInstance(FreezeDetector.class);
  
  private static final long SLEEP_INTERVAL_MS = 5 * 1000L; // 5 seconds
  private static final long EXPECTED_DELTA_MS = SLEEP_INTERVAL_MS + 2 * 1000L;
  
  private volatile boolean myStopped;
  private long myPeriodBegin = -1L;
  private final List<TimeRange> myPeriods = Collections.synchronizedList(new ArrayList<>());

  public FreezeDetector() {
  }

  public static final class TimeRange {
    public final long begin;
    public final long end;

    public TimeRange(long begin, long end) {
      this.begin = begin;
      this.end = end;
    }
  }

  public void start() {
    final Thread thread = new Thread(() -> runDetectLoop(), "FreezeDetector Thread");
    thread.setDaemon(true);
    thread.start();
  }
  
  public void stop() {
    myStopped = true;
  }

  public long getAdjustedDuration(final long begin, final long end) {
    long duration = end - begin;
    synchronized (myPeriods) {
      for (TimeRange freeze : myPeriods) {
        final long freezeStart = freeze.begin;
        if (freezeStart > begin  && freezeStart < end) {
          duration -= (Math.min(end, freeze.end) - freezeStart);
        }
      }
    }
    return duration;
  }

  @SuppressWarnings("BusyWait")
  private void runDetectLoop() {
    while (!myStopped) {
      final long pauseDuration = checkFreeze();
      if (pauseDuration > 0L) {
        try {
          Thread.sleep(pauseDuration);
        }
        catch (InterruptedException ignored) {
        }
      }
    }
  }

  private long checkFreeze() {
    final long current = System.currentTimeMillis();
    final long previous = myPeriodBegin;
    if (previous > 0L && current - previous > EXPECTED_DELTA_MS) {
      myPeriods.add(new TimeRange(previous, current));
      LOG.info("System sleep/hibernate detected from " + new Date(previous) + " till " + new Date(current) + "; " + StringUtil.formatDuration(current - previous));
    }
    myPeriodBegin = current;
    return SLEEP_INTERVAL_MS - (System.currentTimeMillis() - current);
  }
}
