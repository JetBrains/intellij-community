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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class KeyedExtensionCollector<T, KeyT> {
  private final Map<String, List<KeyedLazyInstance<T>>> myRegistry = new THashMap<String, List<KeyedLazyInstance<T>>>();
  private final Map<String, List<T>> myExplicitExtensions = new THashMap<String, List<T>>();
  private final Map<String, List<T>> myCache = new HashMap<String, List<T>>();
  private final Object lock = new Object();

  public KeyedExtensionCollector(@NonNls String epName) {
    ExtensionPointName<KeyedLazyInstance<T>> typesafe = ExtensionPointName.create(epName);
    if (Extensions.getRootArea().hasExtensionPoint(epName)) {
      final ExtensionPoint<KeyedLazyInstance<T>> point = Extensions.getRootArea().getExtensionPoint(typesafe);
      point.addExtensionPointListener(new ExtensionPointListener<KeyedLazyInstance<T>>() {
        public void extensionAdded(final KeyedLazyInstance<T> bean, @Nullable final PluginDescriptor pluginDescriptor) {
          synchronized (lock) {
            List<KeyedLazyInstance<T>> beans = myRegistry.get(bean.getKey());
            if (beans == null) {
              beans = new CopyOnWriteArrayList<KeyedLazyInstance<T>>();
              myRegistry.put(bean.getKey(), beans);
            }
            beans.add(bean);
            myCache.remove(bean.getKey());
          }
        }

        public void extensionRemoved(final KeyedLazyInstance<T> bean, @Nullable final PluginDescriptor pluginDescriptor) {
          synchronized (lock) {
            List<KeyedLazyInstance<T>> beans = myRegistry.get(bean.getKey());
            if (beans != null) {
              beans.remove(bean);
            }
            myCache.remove(bean.getKey());
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
    final List<KeyedLazyInstance<T>> beans = myRegistry.get(key);
    if (beans != null) {
      for (KeyedLazyInstance<T> bean : beans) {
        result.add(bean.getInstance());
      }
    }
    return result;
  }
}