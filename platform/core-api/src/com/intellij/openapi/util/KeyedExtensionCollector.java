// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.openapi.util;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
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

public class KeyedExtensionCollector<T, KeyT> implements ModificationTracker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.KeyedExtensionCollector");

  private final Map<String, List<T>> myExplicitExtensions = new THashMap<>(); // guarded by lock
  private final ConcurrentMap<String, List<T>> myCache = ContainerUtil.newConcurrentMap();

  @NonNls protected final String lock;

  private final String myEpName;
  private final SimpleModificationTracker myTracker = new SimpleModificationTracker();

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
        myCache.remove(bean.getKey());
        myTracker.incModificationCount();
      }
    }

    @Override
    public void extensionRemoved(@NotNull final KeyedLazyInstance<T> bean, @Nullable final PluginDescriptor pluginDescriptor) {
      synchronized (lock) {
        myCache.remove(bean.getKey());
        myTracker.incModificationCount();
      }
    }

    @Override
    public void areaReplaced(@NotNull final ExtensionsArea area) {
      myCache.clear();
      myTracker.incModificationCount();
    }
  };
  private final ExtensionPointAvailabilityListener myExtensionPointAvailabilityListener;

  public KeyedExtensionCollector(@NonNls @NotNull String epName) {
    myEpName = epName;
    lock = "lock for KeyedExtensionCollector " + epName;
    myExtensionPointAvailabilityListener = new ExtensionPointAvailabilityListener() {
      @Override
      public void extensionPointRegistered(@NotNull ExtensionPoint extensionPoint) {
        if (extensionPoint.getName().equals(epName)) {
          //noinspection unchecked
          ((ExtensionPointImpl)extensionPoint).addExtensionPointListener(myListener, false, null);
          myCache.clear();
          myTracker.incModificationCount();
        }
      }

      @Override
      public void extensionPointRemoved(@NotNull ExtensionPoint extensionPoint) {
        // no need to remove myListener - it should deregister automatically
      }
    };
    Extensions.getRootArea().addAvailabilityListener(epName, myExtensionPointAvailabilityListener);
  }

  public KeyedExtensionCollector(@NonNls @NotNull String epName, @Nullable Disposable parentDisposable) {
    this(epName);
    if (parentDisposable == null) return;
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        ExtensionsArea area = Extensions.getRootArea();
        area.removeAvailabilityListener(epName, myExtensionPointAvailabilityListener);
        if (area.hasExtensionPoint(epName)) {
          ExtensionPoint point = area.getExtensionPoint(epName);
          //noinspection unchecked
          point.removeExtensionPointListener(myListener);
        }
      }
    });
  }

  public void addExplicitExtension(@NotNull KeyT key, @NotNull T t) {
    synchronized (lock) {
      final String skey = keyToString(key);
      List<T> list = myExplicitExtensions.computeIfAbsent(skey, __ -> new SmartList<>());
      list.add(t);
      myCache.remove(skey);
      myTracker.incModificationCount();
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
      myTracker.incModificationCount();
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

  protected List<T> buildExtensionsFromExtensionPoint(@Nullable List<T> result, @NotNull Predicate<? super KeyedLazyInstance<T>> isMyBean) {
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
  protected List<T> buildExtensions(@NotNull Set<String> keys) {
    synchronized (lock) {
      List<T> result = buildExtensionsFromExplicitRegistration(null, key -> keys.contains(key));
      result = buildExtensionsFromExtensionPoint(result, bean -> keys.contains(bean.getKey()));
      return ContainerUtil.notNullize(result);
    }
  }

  @Nullable
  protected List<T> buildExtensionsFromExplicitRegistration(@Nullable List<T> result, Condition<String> isMyBean) {
    for (Map.Entry<String, List<T>> entry : myExplicitExtensions.entrySet()) {
      String key = entry.getKey();
      if (isMyBean.value(key)) {
        List<T> list = entry.getValue();
        if (result == null) {
          result = new ArrayList<>(list);
        }
        else {
          result.addAll(list);
        }
      }
    }
    return result;
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

  @NotNull
  public String getName() {
    return myEpName;
  }

  @Override
  public long getModificationCount() {
    return myTracker.getModificationCount();
  }

  protected void ensureValuesLoaded() {
    ExtensionPoint<KeyedLazyInstance<T>> point = getPoint();
    if (point != null) {
      for (KeyedLazyInstance<T> bean : point.getExtensionList()) {
        bean.getInstance();
      }
    }
  }

}
