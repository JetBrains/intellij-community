// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class FreezeDetector {
  private static final long SLEEP_INTERVAL_MS = 5 * 1000L; // 5 seconds
  private static final long EXPECTED_DELTA_MS = SLEEP_INTERVAL_MS + 2 * 1000L;
  
  private volatile boolean myStopped;
  private final Future<?> myFuture;
  private long myPeriodBegin = -1L;
  private final List<TimeRange> myPeriods = Collections.synchronizedList(new ArrayList<>());

  public FreezeDetector(ExecutorService executor) {
    myFuture = executor.submit(() -> runDetectLoop());
  }

  public interface TimeRange {
    long begin();
    long end();

    default long duration() {
      return end() - begin();
    }

    static TimeRange create(final long begin, final long end) {
      return new TimeRange() {
        @Override
        public long begin() {
          return begin;
        }

        @Override
        public long end() {
          return end;
        }
      };
    }
  }

  public void stop() {
    myStopped = true;
    myFuture.cancel(true);
  }

  public long getAdjustedDuration(long begin, long end) {
    return getAdjustedDuration(TimeRange.create(begin, end));
  }
  
  public long getAdjustedDuration(TimeRange range) {
    long duration = range.duration();
    synchronized (myPeriods) {
      for (TimeRange freeze : myPeriods) {
        final long freezeStart = freeze.begin();
        if (freezeStart > range.begin()  && freezeStart < range.end()) {
          long end = Math.min(range.end(), freeze.end());
          duration -= (end - freezeStart);
        }
      }
    }
    return duration;
  }

  public List<TimeRange> getFreezePeriods() {
    return Collections.unmodifiableList(myPeriods);
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
      myPeriods.add(TimeRange.create(previous, current));
    }
    myPeriodBegin = current;
    return SLEEP_INTERVAL_MS - (System.currentTimeMillis() - current);
  }
}
