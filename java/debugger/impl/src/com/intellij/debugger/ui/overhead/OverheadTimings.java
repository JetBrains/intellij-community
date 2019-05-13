// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.overhead;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author egor
 */
public class OverheadTimings {
  public static final Key<OverheadTimings> KEY = Key.create("OVERHEAD_TIMINGS");

  private final EventDispatcher<OverheadTimingsListener> myEventDispatcher = EventDispatcher.create(OverheadTimingsListener.class);
  private final Map<OverheadProducer, Timings> myMap = new ConcurrentHashMap<>();

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final List<Pair<Long, Timings>> myLast10Elements = new LinkedList<Pair<Long, Timings>>() {
    private long totalTime = 0;

    @Override
    public synchronized boolean add(Pair<Long, Timings> o) {
      if (size() > 10) {
        if (isExcessive()) {
          myEventDispatcher.getMulticaster().excessiveOverheadDetected();
        }
        Pair<Long, Timings> first = removeFirst();
        if (first != null) {
          totalTime -= ObjectUtils.notNull(first.getSecond().myTime, 0L);
        }
      }
      totalTime += ObjectUtils.notNull(o.getSecond().myTime, 0L);
      return super.add(o);
    }

    private boolean isExcessive() {
      long timeframe = getLast().first - getFirst().first;
      return totalTime > timeframe || timeframe < 5;
    }
  };

  public static Long getTime(DebugProcess process, OverheadProducer producer) {
    Timings timings = getTimings(process).myMap.get(producer);
    return timings != null ? timings.myTime : null;
  }

  public static long getHits(DebugProcess process, OverheadProducer producer) {
    Timings timings = getTimings(process).myMap.get(producer);
    return timings != null ? timings.myHits : 0;
  }

  public static Set<OverheadProducer> getProducers(DebugProcess process) {
    return getTimings(process).myMap.keySet();
  }

  public static void add(DebugProcess process, OverheadProducer producer, long hits, @Nullable Long overhead) {
    OverheadTimings timings = getTimings(process);
    Timings newTiming = new Timings(hits, overhead);
    timings.myLast10Elements.add(Pair.create(System.currentTimeMillis(), newTiming));
    timings.myMap.merge(producer, newTiming, (old, value) -> {
      Long newTime = old.myTime;
      if (value.myTime != null) {
        newTime += value.myTime;
      }
      return new Timings(old.myHits + value.myHits, newTime);
    });
    timings.myEventDispatcher.getMulticaster().timingAdded(producer);
  }

  @NotNull
  private static OverheadTimings getTimings(DebugProcess process) {
    OverheadTimings data = process.getUserData(KEY);
    if (data == null) {
      data = new OverheadTimings();
      process.putUserData(KEY, data);
    }
    return data;
  }

  private static class Timings {
    final long myHits;
    final Long myTime;

    Timings(long hits, Long time) {
      myHits = hits;
      myTime = time;
    }
  }

  static void addListener(OverheadTimingsListener listener, DebugProcess process) {
    getTimings(process).myEventDispatcher.addListener(listener);
  }

  public interface OverheadTimingsListener extends EventListener {
    void timingAdded(OverheadProducer producer);

    void excessiveOverheadDetected();
  }
}
