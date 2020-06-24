// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public class KeyedExtensionCollector<T, KeyT> implements ModificationTracker {
  private static final Logger LOG = Logger.getInstance(KeyedExtensionCollector.class);

  protected final String myLock;

  /** Guarded by {@link #myLock} */
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private Map<String, List<T>> myExplicitExtensions = Collections.emptyMap();

  private final ConcurrentMap<String, List<T>> myCache = new ConcurrentHashMap<>();
  private final String myEpName;
  private final SimpleModificationTracker myTracker = new SimpleModificationTracker();

  protected final AtomicBoolean myEpListenerAdded = new AtomicBoolean();

  public KeyedExtensionCollector(@NotNull ExtensionPointName<KeyedLazyInstance<T>> epName) {
    this(epName.getName());
  }

  public KeyedExtensionCollector(@NotNull String epName) {
    myEpName = epName;
    myLock = "lock for KeyedExtensionCollector " + epName;
  }

  public void clearCache() {
    myCache.clear();
    myTracker.incModificationCount();
  }

  private void addExtensionPointListener(@NotNull ExtensionPoint<KeyedLazyInstance<T>> point) {
    if (myEpListenerAdded.compareAndSet(false, true)) {
      point.addExtensionPointListener(new MyExtensionPointListener(), false, null);
    }
  }

  protected void invalidateCacheForExtension(String key) {
    if (key != null) {
      myCache.remove(key);
    }
    myTracker.incModificationCount();
  }

  public void addExplicitExtension(@NotNull KeyT key, @NotNull T t) {
    synchronized (myLock) {
      final String stringKey = keyToString(key);
      if (myExplicitExtensions == Collections.<String, List<T>>emptyMap()) {
        myExplicitExtensions = new HashMap<>();
      }
      List<T> list = myExplicitExtensions.computeIfAbsent(stringKey, __ -> new SmartList<>());
      list.add(t);
      invalidateCacheForExtension(stringKey);
    }
  }

  public void addExplicitExtension(@NotNull KeyT key, @NotNull T t, @NotNull Disposable parentDisposable) {
    addExplicitExtension(key, t);
    Disposer.register(parentDisposable, () -> removeExplicitExtension(key, t));
  }

  public void removeExplicitExtension(@NotNull KeyT key, @NotNull T t) {
    synchronized (myLock) {
      final String stringKey = keyToString(key);
      List<T> list = myExplicitExtensions.get(stringKey);
      if (list != null) {
        list.remove(t);
        if (list.isEmpty()) {
          myExplicitExtensions.remove(stringKey);
        }
      }
      invalidateCacheForExtension(stringKey);
    }
  }

  protected @NotNull String keyToString(@NotNull KeyT key) {
    return key.toString();
  }

  /**
   * @see #findSingle(Object)
   */
  public @NotNull List<T> forKey(@NotNull KeyT key) {
    final String stringKey = keyToString(key);

    List<T> cached = myCache.get(stringKey);
    if (cached == null) {
      List<T> list = buildExtensions(stringKey, key);
      // tiny optimisations to save memory
      cached = ContainerUtil.freeze(list);
      cached = ConcurrencyUtil.cacheOrGet(myCache, stringKey, cached);
    }
    return cached;
  }

  public T findSingle(@NotNull KeyT key) {
    List<T> list = forKey(key);
    return list.isEmpty() ? null : list.get(0);
  }

  protected @NotNull List<T> buildExtensions(@NotNull String stringKey, @NotNull KeyT key) {
    // compute out of our lock (https://youtrack.jetbrains.com/issue/IDEA-208060)
    List<KeyedLazyInstance<T>> extensions = getExtensions();
    synchronized (myLock) {
      List<T> list = myExplicitExtensions.get(stringKey);
      List<T> result = list != null ? new ArrayList<>(list) : null;
      result = buildExtensionsFromExtensionPoint(result, bean -> stringKey.equals(bean.getKey()), extensions);
      return ContainerUtil.notNullize(result);
    }
  }

  // must be called not under our lock
  protected final @NotNull List<KeyedLazyInstance<T>> getExtensions() {
    ExtensionPoint<KeyedLazyInstance<T>> point = getPoint();
    if (point == null) {
      return Collections.emptyList();
    }
    else {
      addExtensionPointListener(point);
      return point.getExtensionList();
    }
  }

  final @Nullable List<T> buildExtensionsFromExtensionPoint(@Nullable List<T> result,
                                                            @NotNull Predicate<? super KeyedLazyInstance<T>> isMyBean,
                                                            @NotNull List<? extends KeyedLazyInstance<T>> extensions) {
    for (KeyedLazyInstance<T> bean : extensions) {
      if (!isMyBean.test(bean)) {
        continue;
      }

      final T instance;
      try {
        instance = bean.getInstance();
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (ExtensionNotApplicableException ignore) {
        continue;
      }
      catch (Exception | LinkageError e) {
        LOG.error(e);
        continue;
      }

      if (result == null) {
        result = new SmartList<>();
      }
      result.add(instance);
    }
    return result;
  }

  protected @NotNull List<T> buildExtensions(@NotNull Set<String> keys) {
    List<KeyedLazyInstance<T>> extensions = getExtensions();
    synchronized (myLock) {
      List<T> result = buildExtensionsFromExplicitRegistration(null, keys::contains);
      result = buildExtensionsFromExtensionPoint(result, bean -> keys.contains(bean.getKey()), extensions);
      return ContainerUtil.notNullize(result);
    }
  }

  protected @Nullable List<T> buildExtensionsFromExplicitRegistration(@Nullable List<T> result, @NotNull Predicate<? super String> isMyBean) {
    for (Map.Entry<String, List<T>> entry : myExplicitExtensions.entrySet()) {
      String key = entry.getKey();
      if (isMyBean.test(key)) {
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

  @ApiStatus.Internal
  public final @Nullable ExtensionPoint<KeyedLazyInstance<T>> getPoint() {
    return Extensions.getRootArea().getExtensionPointIfRegistered(myEpName);
  }

  public boolean hasAnyExtensions() {
    synchronized (myLock) {
      if (!myExplicitExtensions.isEmpty()) {
        return true;
      }

      ExtensionPoint<KeyedLazyInstance<T>> point = getPoint();
      return point != null && point.size() != 0;
    }
  }

  public @NotNull String getName() {
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

  private final class MyExtensionPointListener implements ExtensionPointAndAreaListener<KeyedLazyInstance<T>>, ExtensionPointPriorityListener {
    @Override
    public void extensionAdded(@NotNull KeyedLazyInstance<T> bean, @NotNull PluginDescriptor pluginDescriptor) {
      synchronized (myLock) {
        if (bean.getKey() == null) {
          throw new PluginException("No key specified for extension of class " + bean.getInstance().getClass(), pluginDescriptor.getPluginId());
        }
        invalidateCacheForExtension(bean.getKey());
      }
    }

    @Override
    public void extensionRemoved(@NotNull KeyedLazyInstance<T> bean, @NotNull PluginDescriptor pluginDescriptor) {
      synchronized (myLock) {
        invalidateCacheForExtension(bean.getKey());
      }
    }

    @Override
    public void areaReplaced(@NotNull ExtensionsArea area) {
      myCache.clear();
      myTracker.incModificationCount();
    }
  }
}