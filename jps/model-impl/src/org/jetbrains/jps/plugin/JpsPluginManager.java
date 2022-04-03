/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.plugin;

import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

@ApiStatus.Internal
public abstract class JpsPluginManager {
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  @Nullable
  private static JpsPluginManager ourInstance = null;

  private static final Object ourSyncRoot = new Object();

  @NotNull
  public static JpsPluginManager getInstance() {
    JpsPluginManager instance = ourInstance;
    if (instance != null && instance.isFullyLoaded()) {
      return instance;
    }

    synchronized (ourSyncRoot) {
      JpsPluginManager pluginManager;

      Iterator<JpsPluginManager> managers = ServiceLoader.load(JpsPluginManager.class, JpsPluginManager.class.getClassLoader()).iterator();
      if (managers.hasNext()) {
        try {
          pluginManager = managers.next();
        }
        catch (ServiceConfigurationError e) {
          Throwable cause = e.getCause();
          if (cause instanceof ProcessCanceledException) {
            throw (ProcessCanceledException)cause;
          }
          throw e;
        }
        if (managers.hasNext()) {
          throw new ServiceConfigurationError("More than one implementation of " + JpsPluginManager.class + " found: " + pluginManager.getClass() + " and " + managers.next().getClass());
        }
      }
      else {
        pluginManager = new SingleClassLoaderPluginManager();
      }

      ourInstance = pluginManager;
      return pluginManager;
    }
  }

  public static void setInstance(JpsPluginManager instance) {
    synchronized (ourSyncRoot) {
      if (ourInstance != null) {
        throw new IllegalStateException("JpsPluginManager instance was already initialized to " + ourInstance.getClass().getName());
      }

      ourInstance = instance;
    }
  }

  public static JpsPluginManager tryGetInstance() {
    return ourInstance;
  }

  @NotNull
  public abstract <T> Collection<T> loadExtensions(@NotNull Class<T> extensionClass);

  public abstract boolean isFullyLoaded();

  public abstract int getModificationStamp();
}
