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

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.openapi.util.Key;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

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
  private final Map<Object, Timings> myMap = new ConcurrentHashMap<>();

  public static long getTime(DebugProcessImpl process, Object producer) {
    Timings timings = getTimings(process).myMap.get(producer);
    return timings != null ? timings.myTime : 0;
  }

  public static long getHits(DebugProcessImpl process, Object producer) {
    Timings timings = getTimings(process).myMap.get(producer);
    return timings != null ? timings.myHits : 0;
  }

  public static Set<Object> getProducers(DebugProcessImpl process) {
    return getTimings(process).myMap.keySet();
  }

  public static void add(DebugProcessImpl process, Object producer, long overhead) {
    OverheadTimings timings = getTimings(process);
    timings.myMap.merge(producer, new Timings(1, overhead), (old, value) -> new Timings(old.myHits + 1, old.myTime + overhead));
    timings.myEventDispatcher.getMulticaster().timingAdded(producer);
  }

  @NotNull
  private static OverheadTimings getTimings(DebugProcessImpl process) {
    OverheadTimings data = process.getUserData(KEY);
    if (data == null) {
      data = new OverheadTimings();
      process.putUserData(KEY, data);
    }
    return data;
  }

  private static class Timings {
    final long myHits;
    final long myTime;

    public Timings(long hits, long time) {
      myHits = hits;
      myTime = time;
    }
  }

  static void addListener(OverheadTimingsListener listener, DebugProcessImpl process) {
    getTimings(process).myEventDispatcher.addListener(listener);
  }

  public interface OverheadTimingsListener extends EventListener {
    void timingAdded(Object producer);
  }
}
