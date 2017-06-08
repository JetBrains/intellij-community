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
package com.intellij.debugger.ui;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.openapi.util.Key;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author egor
 */
public class OverheadTimings {
  public static final Key<OverheadTimings> KEY = Key.create("OVERHEAD_TIMINGS");

  private final Map<Object, Long> myMap = new ConcurrentHashMap<>();

  public static float get(DebugProcessImpl process, Object producer) {
    return getTimings(process).myMap.get(producer);
  }

  public static void add(DebugProcessImpl process, Object producer, long overhead) {
    getTimings(process).myMap.merge(producer, overhead, (old, value) -> old + value);
  }

  private static OverheadTimings getTimings(DebugProcessImpl process) {
    OverheadTimings data = process.getUserData(KEY);
    if (data == null) {
      data = new OverheadTimings();
      process.putUserData(KEY, data);
    }
    return data;
  }
}
