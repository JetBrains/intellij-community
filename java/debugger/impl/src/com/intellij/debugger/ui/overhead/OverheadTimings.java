/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.ui.overhead;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.openapi.util.Key;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author egor
 */
public class OverheadTimings {
  public static final Key<OverheadTimings> KEY = Key.create("OVERHEAD_TIMINGS");

  private final EventDispatcher<OverheadTimingsListener> myEventDispatcher = EventDispatcher.create(OverheadTimingsListener.class);
  private final Map<OverheadProducer, Timings> myMap = new ConcurrentHashMap<>();

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

  public static void add(DebugProcess process, OverheadProducer producer, @Nullable Long overhead) {
    OverheadTimings timings = getTimings(process);
    timings.myMap.merge(producer, new Timings(1, overhead), (old, value) -> {
      Long newTime = old.myTime;
      if (value.myTime != null) {
        newTime += value.myTime;
      }
      return new Timings(old.myHits + 1, newTime);
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

    public Timings(long hits, Long time) {
      myHits = hits;
      myTime = time;
    }
  }

  static void addListener(OverheadTimingsListener listener, DebugProcess process) {
    getTimings(process).myEventDispatcher.addListener(listener);
  }

  public interface OverheadTimingsListener extends EventListener {
    void timingAdded(OverheadProducer producer);
  }
}
