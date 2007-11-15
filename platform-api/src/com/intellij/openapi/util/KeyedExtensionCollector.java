/*
 * @author max
 */
package com.intellij.openapi.util;

import com.intellij.openapi.extensions.*;
import com.intellij.util.KeyedLazyInstanceEP;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"})
public abstract class KeyedExtensionCollector<T, KeyT> {
  private final Map<String, List<KeyedLazyInstanceEP<T>>> myRegistry = new THashMap<String, List<KeyedLazyInstanceEP<T>>>();
  private final Map<String, List<T>> myExplicitExtensions = new THashMap<String, List<T>>();
  private final Map<String, List<T>> myCache = new HashMap<String, List<T>>();
  private final Object lock = new Object();

  public KeyedExtensionCollector(@NonNls String epName) {
    ExtensionPointName<KeyedLazyInstanceEP<T>> typesafe = ExtensionPointName.create(epName);
    final ExtensionPoint<KeyedLazyInstanceEP<T>> point = Extensions.getRootArea().getExtensionPoint(typesafe);
    point.addExtensionPointListener(new ExtensionPointListener<KeyedLazyInstanceEP<T>>() {
      public void extensionAdded(final KeyedLazyInstanceEP<T> bean, @Nullable final PluginDescriptor pluginDescriptor) {
        synchronized (lock) {
          List<KeyedLazyInstanceEP<T>> beans = myRegistry.get(bean.key);
          if (beans == null) {
            beans = new CopyOnWriteArrayList<KeyedLazyInstanceEP<T>>();
            myRegistry.put(bean.key, beans);
          }
          beans.add(bean);
          myCache.remove(bean.key);
        }
      }

      public void extensionRemoved(final KeyedLazyInstanceEP<T> bean, @Nullable final PluginDescriptor pluginDescriptor) {
        synchronized (lock) {
          List<KeyedLazyInstanceEP<T>> beans = myRegistry.get(bean.key);
          if (beans != null) {
            beans.remove(bean);
          }
          myCache.remove(bean.key);
        }
      }
    });
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
    final List<KeyedLazyInstanceEP<T>> beans = myRegistry.get(key);
    if (beans != null) {
      for (KeyedLazyInstanceEP<T> bean : beans) {
        result.add(bean.getInstance());
      }
    }
    return result;
  }
}