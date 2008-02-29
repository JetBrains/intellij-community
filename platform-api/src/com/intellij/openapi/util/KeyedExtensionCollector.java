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

public abstract class KeyedExtensionCollector<T, KeyT> {
  private final Map<String, List<T>> myExplicitExtensions = new THashMap<String, List<T>>();
  private final Map<String, List<T>> myCache = new HashMap<String, List<T>>();
  private final Object lock = new Object();
  private ExtensionPoint<KeyedLazyInstance<T>> myPoint;
  private final String myEpName;
  private ExtensionPointAndAreaListener<KeyedLazyInstance<T>> myListener;

  public KeyedExtensionCollector(@NonNls String epName) {
    myEpName = epName;
    resetAreaListener();
  }

  private void resetAreaListener() {
    synchronized (lock) {
      myCache.clear();

      if (myPoint != null) {
        myPoint.removeExtensionPointListener(myListener);
        myPoint = null;
        myListener = null;
      }
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
        cache = buildExtensions(stringKey, key);
        myCache.put(stringKey, cache);
      }
      return cache;
    }
  }

  protected List<T> buildExtensions(final String stringKey, final KeyT key) {
    final List<T> explicit = myExplicitExtensions.get(stringKey);
    List<T> result = explicit != null ? new ArrayList<T>(explicit) : new ArrayList<T>();
    final ExtensionPoint<KeyedLazyInstance<T>> point = getPoint();
    if (point != null) {
      final KeyedLazyInstance<T>[] beans = point.getExtensions();
      for (KeyedLazyInstance<T> bean : beans) {
        if (stringKey.equals(bean.getKey())) {
          result.add(bean.getInstance());
        }
      }
    }
    return result;
  }

  @Nullable
  private ExtensionPoint<KeyedLazyInstance<T>> getPoint() {
    if (myPoint == null) {
      if (Extensions.getRootArea().hasExtensionPoint(myEpName)) {
        ExtensionPointName<KeyedLazyInstance<T>> typesafe = ExtensionPointName.create(myEpName);
        myPoint = Extensions.getRootArea().getExtensionPoint(typesafe);
        myListener = new ExtensionPointAndAreaListener<KeyedLazyInstance<T>>() {
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
            resetAreaListener();
          }
        };

        myPoint.addExtensionPointListener(myListener);
      }
    }

    return myPoint;
  }

  public boolean hasAnyExtensions() {
    synchronized (lock) {
      if (!myExplicitExtensions.isEmpty()) return true;
      final ExtensionPoint<KeyedLazyInstance<T>> point = getPoint();
      return point != null && point.getExtensions().length > 0;
    }
  }
}