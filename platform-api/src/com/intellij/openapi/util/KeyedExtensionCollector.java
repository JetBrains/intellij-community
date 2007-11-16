/*
 * @author max
 */
package com.intellij.openapi.util;

import com.intellij.openapi.extensions.*;
import com.intellij.util.KeyedLazyInstance;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class KeyedExtensionCollector<T, KeyT> {
  private final Map<String, List<T>> myExplicitExtensions = new THashMap<String, List<T>>();
  private final Map<String, List<T>> myCache = new HashMap<String, List<T>>();
  private final Object lock = new Object();
  private ExtensionPoint<KeyedLazyInstance<T>> myPoint;

  public KeyedExtensionCollector(@NonNls String epName) {
    ExtensionPointName<KeyedLazyInstance<T>> typesafe = ExtensionPointName.create(epName);
    if (Extensions.getRootArea().hasExtensionPoint(epName)) {
      myPoint = Extensions.getRootArea().getExtensionPoint(typesafe);
      myPoint.addExtensionPointListener(new ExtensionPointAndAreaListener<KeyedLazyInstance<T>>() {
        public void extensionAdded(final KeyedLazyInstance<T> bean, @Nullable final PluginDescriptor pluginDescriptor) {
          synchronized (lock) {
            myCache.remove(bean.getKey());
          }
        }

        public void extensionRemoved(final KeyedLazyInstance<T> bean, @Nullable final PluginDescriptor pluginDescriptor) {
          synchronized (lock) {
            myCache.remove(bean.getKey());
          }
        }

        public void areaReplaced(final ExtensionsArea area) {
          synchronized (lock) {
            dropCaches();
          }
        }
      });
    }
  }

  public void addExpicitExtension(KeyT key, T t) {
    synchronized (lock) {
      final String skey = keyToString(key);
      List<T> list = myExplicitExtensions.get(skey);
      if (list == null) {
        list = new ArrayList<T>();
        myExplicitExtensions.put(skey, list);
      }
      list.add(t);
      myCache.remove(skey);
    }
  }

  public void removeExpicitExtension(KeyT key, T t) {
    synchronized (lock) {
      final String skey = keyToString(key);
      List<T> list = myExplicitExtensions.get(skey);
      if (list != null) {
        list.remove(t);
        myCache.remove(skey);
      }
    }
  }

  protected abstract String keyToString(KeyT key);

  @NotNull
  public List<T> forKey(KeyT key) {
    synchronized (lock) {
      final String stringKey = keyToString(key);
      List<T> cache = myCache.get(stringKey);
      if (cache == null) {
        cache = buildExtensions(stringKey);
        myCache.put(stringKey, cache);
      }
      return cache;
    }
  }

  private List<T> buildExtensions(final String key) {
    final List<T> explicit = myExplicitExtensions.get(key);
    List<T> result = explicit != null ? new ArrayList<T>(explicit) : new ArrayList<T>();
    if (myPoint != null) {
      final KeyedLazyInstance<T>[] beans = myPoint.getExtensions();
      for (KeyedLazyInstance<T> bean : beans) {
        if (key.equals(bean.getKey())) {
          result.add(bean.getInstance());
        }
      }
    }
    return result;
  }

  @TestOnly
  public void dropCaches() {
    myCache.clear();
  }
}