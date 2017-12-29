/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

public class KeyedExtensionCollector<T, KeyT> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.KeyedExtensionCollector");

  private final Map<String, List<T>> myExplicitExtensions = new THashMap<>(); // guarded by lock
  private final ConcurrentMap<String, List<T>> myCache = ContainerUtil.newConcurrentMap();

  @NonNls private final String lock;

  private final String myEpName;
  private final List<ExtensionPointListener<T>> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final ExtensionPointAndAreaListener<KeyedLazyInstance<T>> myListener = new ExtensionPointAndAreaListener<KeyedLazyInstance<T>>() {
    @Override
    public void extensionAdded(@NotNull final KeyedLazyInstance<T> bean, @Nullable final PluginDescriptor pluginDescriptor) {
      synchronized (lock) {
        if (bean.getKey() == null) {
          if (pluginDescriptor != null) {
            throw new PluginException("No key specified for extension of class " + bean.getInstance().getClass(),
                                      pluginDescriptor.getPluginId());
          }
          LOG.error("No key specified for extension of class " + bean.getInstance().getClass());
          return;
        }
        String skey = bean.getKey();
        myCache.remove(skey);
        for (ExtensionPointListener<T> listener : myListeners) {
          listener.extensionAdded(bean.getInstance(), null);
        }
      }
    }

    @Override
    public void extensionRemoved(@NotNull final KeyedLazyInstance<T> bean, @Nullable final PluginDescriptor pluginDescriptor) {
      synchronized (lock) {
        String skey = bean.getKey();
        myCache.remove(skey);
        for (ExtensionPointListener<T> listener : myListeners) {
          listener.extensionRemoved(bean.getInstance(), null);
        }
      }
    }

    @Override
    public void areaReplaced(@NotNull final ExtensionsArea area) {
      myCache.clear();
    }
  };

  public KeyedExtensionCollector(@NonNls @NotNull String epName) {
    myEpName = epName;
    lock = "lock for KeyedExtensionCollector " + epName;
    Extensions.getRootArea().addAvailabilityListener(epName, new ExtensionPointAvailabilityListener() {
      @Override
      public void extensionPointRegistered(@NotNull ExtensionPoint extensionPoint) {
        if (extensionPoint.getName().equals(epName)) {
          //noinspection unchecked
          extensionPoint.addExtensionPointListener(myListener);
          myCache.clear();
        }
      }

      @Override
      public void extensionPointRemoved(@NotNull ExtensionPoint extensionPoint) {
        // no need to remove myListener - it should deregister automatically
      }
    });
  }

  public void addExplicitExtension(@NotNull KeyT key, @NotNull T t) {
    synchronized (lock) {
      final String skey = keyToString(key);
      List<T> list = myExplicitExtensions.computeIfAbsent(skey, __ -> new SmartList<>());
      list.add(t);
      myCache.remove(skey);
      for (ExtensionPointListener<T> listener : myListeners) {
        listener.extensionAdded(t, null);
      }
    }
  }

  public void removeExplicitExtension(@NotNull KeyT key, @NotNull T t) {
    synchronized (lock) {
      final String skey = keyToString(key);
      List<T> list = myExplicitExtensions.get(skey);
      if (list != null) {
        list.remove(t);
        if (list.isEmpty()) {
          myExplicitExtensions.remove(skey);
        }
      }
      myCache.remove(skey);
      for (ExtensionPointListener<T> listener : myListeners) {
        listener.extensionRemoved(t, null);
      }
    }
  }

  @NotNull
  protected String keyToString(@NotNull KeyT key) {
    return key.toString();
  }

  /**
   * @see #findSingle(Object)
   */
  @NotNull
  public List<T> forKey(@NotNull KeyT key) {
    final String stringKey = keyToString(key);

    List<T> cached = myCache.get(stringKey);
    if (cached == null) {
      List<T> list = buildExtensions(stringKey, key);
      // tiny optimisations to save memory
      //noinspection unchecked
      cached = list.isEmpty() ? Collections.emptyList() :
               list.size() == 1 ? ContainerUtil.immutableSingletonList(list.get(0)) : ContainerUtil.immutableList((T[])list.toArray());
      cached = ConcurrencyUtil.cacheOrGet(myCache, stringKey, cached);
    }
    return cached;
  }

  public T findSingle(@NotNull KeyT key) {
    List<T> list = forKey(key);
    return list.isEmpty() ? null : list.get(0);
  }

  @NotNull
  protected List<T> buildExtensions(@NotNull String stringKey, @NotNull KeyT key) {
    synchronized (lock) {
      List<T> list = myExplicitExtensions.get(stringKey);
      List<T> result = list == null ? null : new ArrayList<>(list);

      result = buildExtensionsFromExtensionPoint(result, bean -> stringKey.equals(bean.getKey()));
      return result == null ? Collections.emptyList() : result;
    }
  }

  private List<T> buildExtensionsFromExtensionPoint(@Nullable List<T> result, @NotNull Predicate<KeyedLazyInstance<T>> isMyBean) {
    final ExtensionPoint<KeyedLazyInstance<T>> point = getPoint();
    if (point != null) {
      final KeyedLazyInstance<T>[] beans = point.getExtensions();
      for (KeyedLazyInstance<T> bean : beans) {
        if (isMyBean.test(bean)) {
          final T instance;
          try {
            instance = bean.getInstance();
          }
          catch (ProcessCanceledException e) {
            throw e;
          }
          catch (Exception | LinkageError e) {
            LOG.error(e);
            continue;
          }
          if (result == null) result = new SmartList<>();
          result.add(instance);
        }
      }
    }
    return result;
  }

  @NotNull
  protected final List<T> buildExtensions(@NotNull Set<String> keys) {
    synchronized (lock) {
      List<T> result = null;
      for (Map.Entry<String, List<T>> entry : myExplicitExtensions.entrySet()) {
        String key = entry.getKey();
        if (keys.contains(key)) {
          List<T> list = entry.getValue();
          if (result == null) {
            result = new ArrayList<>(list);
          }
          else {
            result.addAll(list);
          }
        }
      }

      result = buildExtensionsFromExtensionPoint(result, bean -> keys.contains(bean.getKey()));
      return result == null ? Collections.emptyList() : result;
    }
  }

  @Nullable
  private ExtensionPoint<KeyedLazyInstance<T>> getPoint() {
    return Extensions.getRootArea().hasExtensionPoint(myEpName) ? Extensions.getRootArea().getExtensionPoint(myEpName) : null;
  }

  public boolean hasAnyExtensions() {
    synchronized (lock) {
      if (!myExplicitExtensions.isEmpty()) return true;
      final ExtensionPoint<KeyedLazyInstance<T>> point = getPoint();
      return point != null && point.hasAnyExtensions();
    }
  }

  public void addListener(@NotNull final ExtensionPointListener<T> listener, @NotNull Disposable parent) {
    myListeners.add(listener);
    Disposer.register(parent, () -> myListeners.remove(listener));
  }

  @NotNull
  public String getName() {
    return myEpName;
  }
}
