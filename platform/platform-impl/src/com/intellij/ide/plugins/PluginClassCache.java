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

import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.extensions.PluginId;
import gnu.trove.THashMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
class PluginClassCache {
  private static final Object PLUGIN_CLASSES_LOCK = new Object();
  private final Map<String, PluginId> ourPluginClasses = new THashMap<String, PluginId>();

  public void addPluginClass(String className, PluginId pluginId) {
    synchronized(PLUGIN_CLASSES_LOCK) {
      ourPluginClasses.put(className, pluginId);
    }
  }

  @Nullable
  private PluginId findLoadingPlugin(String className) {
    for (IdeaPluginDescriptor descriptor : PluginManager.getPlugins()) {
      ClassLoader loader = descriptor.getPluginClassLoader();
      if (loader instanceof PluginClassLoader && ((PluginClassLoader)loader).hasLoadedClass(className)) {
        return descriptor.getPluginId();
      }
    }
    return null;
  }

  public void dumpPluginClassStatistics() {
    if (!Boolean.valueOf(System.getProperty("idea.is.internal")).booleanValue()) return;

    Map<String, ClassCounter> pluginToClassMap = new HashMap<String, ClassCounter>();
    synchronized (PLUGIN_CLASSES_LOCK) {
      for (Map.Entry<String, PluginId> entry : ourPluginClasses.entrySet()) {
        String id = entry.getValue().toString();
        final ClassCounter counter = pluginToClassMap.get(id);
        if (counter != null) {
          counter.increment();
        }
        else {
          pluginToClassMap.put(id, new ClassCounter(id));
        }
      }
    }
    List<ClassCounter> counters = new ArrayList<ClassCounter>(pluginToClassMap.values());
    Collections.sort(counters, new Comparator<ClassCounter>() {
      public int compare(ClassCounter o1, ClassCounter o2) {
        return o2.myCount - o1.myCount;
      }
    });
    for (ClassCounter counter : counters) {
      PluginManager.getLogger().info(counter.toString());
    }
  }

  @Nullable
  public PluginId getPluginByClassName(String className) {
    synchronized (PLUGIN_CLASSES_LOCK) {
      return ourPluginClasses.get(className);
    }
  }

  private static class ClassCounter {
    private final String myPluginId;
    private int myCount;

    private ClassCounter(String pluginId) {
      myPluginId = pluginId;
      myCount = 1;
    }

    private void increment() {
      myCount++;
    }

    @Override
    public String toString() {
      return myPluginId + " loaded " + myCount + " classes";
    }
  }
}
