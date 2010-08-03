/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.concurrency.JBLock;
import com.intellij.util.concurrency.JBReentrantReadWriteLock;
import com.intellij.util.concurrency.LockFactory;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class KeyedExtensionCollector<T, KeyT> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.KeyedExtensionCollector");

  private final Map<String, List<T>> myExplicitExtensions = new THashMap<String, List<T>>();
  private final Map<String, List<T>> myCache = new HashMap<String, List<T>>();

  private final JBLock w;
  private final JBLock r;

  private ExtensionPoint<KeyedLazyInstance<T>> myPoint;
  private final String myEpName;
  private ExtensionPointAndAreaListener<KeyedLazyInstance<T>> myListener;
  private final List<ExtensionPointListener<T>> myListeners = ContainerUtil.createEmptyCOWList();

  public KeyedExtensionCollector(@NonNls String epName) {
    JBReentrantReadWriteLock mutex = LockFactory.createReadWriteLock();
    r = mutex.readLock();
    w = mutex.writeLock();
    myEpName = epName;
    resetAreaListener();
  }

  private void resetAreaListener() {
    w.lock();
    try {
      myCache.clear();

      if (myPoint != null) {
        myPoint.removeExtensionPointListener(myListener);
        myPoint = null;
        myListener = null;
      }
    }
    finally {
      w.unlock();
    }
  }

  public void addExplicitExtension(KeyT key, T t) {
    w.lock();
    try {
      final String skey = keyToString(key);
      List<T> list = myExplicitExtensions.get(skey);
      if (list == null) {
        list = new ArrayList<T>();
        myExplicitExtensions.put(skey, list);
      }
      list.add(t);
      myCache.remove(skey);
      for (ExtensionPointListener<T> listener : myListeners) {
        listener.extensionAdded(t, null);
      }
    }
    finally {
      w.unlock();
    }
  }

  public void removeExplicitExtension(KeyT key, T t) {
    w.lock();
    try {
      final String skey = keyToString(key);
      List<T> list = myExplicitExtensions.get(skey);
      if (list != null) {
        list.remove(t);
        myCache.remove(skey);
      }
      for (ExtensionPointListener<T> listener : myListeners) {
        listener.extensionRemoved(t, null);
      }
    }
    finally {
      w.unlock();
    }
  }

  protected abstract String keyToString(KeyT key);

  @NotNull
  public List<T> forKey(KeyT key) {
    final String stringKey = keyToString(key);
    r.lock();
    List<T> cache;
    try {
      cache = myCache.get(stringKey);
      if (cache != null) return cache;
    }
    finally {
      r.unlock();
    }

    w.lock();
    try {
      cache = buildExtensions(stringKey, key);
      myCache.put(stringKey, cache);
      return cache;
    }
    finally {
      w.unlock();
    }
  }

  protected List<T> buildExtensions(final String stringKey, final KeyT key) {
    return buildExtensions(Collections.singleton(stringKey));
  }

  protected final List<T> buildExtensions(Set<String> keys) {
    List<T> result = null;
    for (Map.Entry<String, List<T>> entry : myExplicitExtensions.entrySet()) {
      String key = entry.getKey();
      if (keys.contains(key)) {
        if (result == null) result = new ArrayList<T>();
        List<T> list = entry.getValue();
        result.addAll(list);
      }
    }

    final ExtensionPoint<KeyedLazyInstance<T>> point = getPoint();
    if (point != null) {
      final KeyedLazyInstance<T>[] beans = point.getExtensions();
      for (KeyedLazyInstance<T> bean : beans) {
        if (keys.contains(bean.getKey())) {
          final T instance;
          try {
            instance = bean.getInstance();
          }
          catch (Exception e) {
            LOG.error(e);
            continue;
          }
          if (result == null) result = new ArrayList<T>();
          result.add(instance);
        }
      }
    }
    return result == null ? Collections.<T>emptyList() : result;
  }

  @Nullable
  private ExtensionPoint<KeyedLazyInstance<T>> getPoint() {
    if (myPoint == null) {
      if (Extensions.getRootArea().hasExtensionPoint(myEpName)) {
        ExtensionPointName<KeyedLazyInstance<T>> typesafe = ExtensionPointName.create(myEpName);
        myPoint = Extensions.getRootArea().getExtensionPoint(typesafe);
        myListener = new ExtensionPointAndAreaListener<KeyedLazyInstance<T>>() {
          public void extensionAdded(final KeyedLazyInstance<T> bean, @Nullable final PluginDescriptor pluginDescriptor) {
            w.lock();
            try {
              myCache.remove(bean.getKey());
              for (ExtensionPointListener<T> listener : myListeners) {
                listener.extensionAdded(bean.getInstance(), null);
              }
            }
            finally {
              w.unlock();
            }
          }

          public void extensionRemoved(final KeyedLazyInstance<T> bean, @Nullable final PluginDescriptor pluginDescriptor) {
            w.lock();
            try {
              myCache.remove(bean.getKey());
              for (ExtensionPointListener<T> listener : myListeners) {
                listener.extensionRemoved(bean.getInstance(), null);
              }
            }
            finally {
              w.unlock();
            }
          }

          public void areaReplaced(final ExtensionsArea area) {
            resetAreaListener();
          }
        };

        myPoint.addExtensionPointListener(myListener);
      }
    }

    return myPoint;
  }

  public boolean hasAnyExtensions() {
    w.lock();
    try {
      if (!myExplicitExtensions.isEmpty()) return true;
      final ExtensionPoint<KeyedLazyInstance<T>> point = getPoint();
      return point != null && point.hasAnyExtensions();
    }
    finally {
      w.unlock();
    }
  }

  public void addListener(@NotNull ExtensionPointListener<T> listener) {
    myListeners.add(listener);
  }

  public void removeListener(@NotNull ExtensionPointListener<T> listener) {
    myListeners.remove(listener);
  }
}
