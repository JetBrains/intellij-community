/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
class PluginClassCache {
  private static final Object ourLock = new Object();
  private final TObjectIntHashMap<PluginId> myClassCounts = new TObjectIntHashMap<>();

  void addPluginClass(@NotNull PluginId pluginId) {
    synchronized(ourLock) {
      myClassCounts.put(pluginId, myClassCounts.get(pluginId) + 1);
    }
  }

  void dumpPluginClassStatistics() {
    if (!Boolean.valueOf(System.getProperty("idea.is.internal")).booleanValue()) return;

    List<PluginId> counters;
    synchronized (ourLock) {
      //noinspection unchecked
      counters = new ArrayList(Arrays.asList(myClassCounts.keys()));
    }

    counters.sort((o1, o2) -> myClassCounts.get(o2) - myClassCounts.get(o1));
    for (PluginId id : counters) {
      PluginManagerCore.getLogger().info(id + " loaded " + myClassCounts.get(id) + " classes");
    }
  }
}
