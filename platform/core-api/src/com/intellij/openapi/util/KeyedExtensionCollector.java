// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.KeyedLazyInstance;
import kotlinx.collections.immutable.PersistentList;
import kotlinx.collections.immutable.PersistentMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static kotlinx.collections.immutable.ExtensionsKt.persistentHashMapOf;
import static kotlinx.collections.immutable.ExtensionsKt.persistentListOf;

public class KeyedExtensionCollector<T, KeyT> implements ModificationTracker {
  private static final Logger LOG = Logger.getInstance(KeyedExtensionCollector.class);

  protected final @NonNls String lock;

  /** Guarded by {@link #lock} */
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private PersistentMap<String, PersistentList<T>> explicitExtensions = persistentHashMapOf();

  private volatile PersistentMap<String, PersistentList<T>> cache = persistentHashMapOf();
  private final String epName;
  private final SimpleModificationTracker tracker = new SimpleModificationTracker();

  protected final AtomicBoolean myEpListenerAdded = new AtomicBoolean();

  public KeyedExtensionCollector(@NotNull ExtensionPointName<? extends KeyedLazyInstance<T>> epName) {
    this(epName.getName());
  }

  public KeyedExtensionCollector(@NotNull String epName) {
    this.epName = epName;
    lock = "lock for KeyedExtensionCollector " + epName;
  }

  public void clearCache() {
    synchronized (lock) {
      cache = cache.clear();
      tracker.incModificationCount();
    }
  }

  private void addExtensionPointListener(@NotNull ExtensionPoint<@NotNull KeyedLazyInstance<T>> point) {
    if (myEpListenerAdded.compareAndSet(false, true)) {
      point.addExtensionPointListener(new MyExtensionPointListener(), false, null);
    }
  }

  protected void invalidateCacheForExtension(String key) {
    synchronized (lock) {
      if (key != null) {
        cache = cache.remove(key);
      }
      tracker.incModificationCount();
    }
  }

  public void addExplicitExtension(@NotNull KeyT key, @NotNull T t) {
    String stringKey = keyToString(key);
    synchronized (lock) {
      PersistentList<T> value = explicitExtensions.get(stringKey);
      explicitExtensions = explicitExtensions.put(stringKey, value == null ? persistentListOf(t) : value.add(t));
      invalidateCacheForExtension(stringKey);
    }
  }

  public void addExplicitExtension(@NotNull KeyT key, @NotNull T t, @NotNull Disposable parentDisposable) {
    addExplicitExtension(key, t);
    Disposer.register(parentDisposable, () -> removeExplicitExtension(key, t));
  }

  public void removeExplicitExtension(@NotNull KeyT key, @NotNull T t) {
    String stringKey = keyToString(key);
    synchronized (lock) {
      PersistentList<T> list = explicitExtensions.get(stringKey);
      if (list != null) {
        list = list.remove(t);
        explicitExtensions = list.isEmpty() ? explicitExtensions.remove(stringKey) : explicitExtensions.put(stringKey, list);
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
  public final @NotNull List<T> forKey(@NotNull KeyT key) {
    String stringKey = keyToString(key);

    PersistentList<T> cached = cache.get(stringKey);
    if (cached != null) {
      return cached;
    }

    cached = buildExtensions(stringKey, key);

    synchronized (lock) {
      PersistentList<T> recent = cache.get(stringKey);
      if (recent != null) {
        return recent;
      }

      cache = cache.put(stringKey, cached);
      return cached;
    }
  }

  public final T findSingle(@NotNull KeyT key) {
    List<T> list = forKey(key);
    return list.isEmpty() ? null : list.get(0);
  }

  protected @NotNull PersistentList<T> buildExtensions(@NotNull String stringKey, @NotNull KeyT key) {
    // compute out of our lock (https://youtrack.jetbrains.com/issue/IDEA-208060)
    List<KeyedLazyInstance<T>> extensions = getExtensions();
    synchronized (lock) {
      PersistentList<T> explicit = explicitExtensions.get(stringKey);
      PersistentList<T> result = buildExtensionsFromExtensionPoint(bean -> stringKey.equals(bean.getKey()), extensions);
      return explicit == null ? result : explicit.addAll(result);
    }
  }

  // must be called not under our lock
  protected final @NotNull List<KeyedLazyInstance<T>> getExtensions() {
    ExtensionPoint<@NotNull KeyedLazyInstance<T>> point = getPoint();
    if (point == null) {
      return Collections.emptyList();
    }
    else {
      addExtensionPointListener(point);
      return point.getExtensionList();
    }
  }

  final @NotNull PersistentList<T> buildExtensionsFromExtensionPoint(@NotNull Predicate<? super KeyedLazyInstance<T>> isMyBean,
                                                                     @NotNull List<? extends KeyedLazyInstance<T>> extensions) {
    PersistentList<T> result = persistentListOf();
    for (KeyedLazyInstance<T> bean : extensions) {
      if (!isMyBean.test(bean)) {
        continue;
      }

      T instance = instantiate(bean);
      if (instance == null) {
        continue;
      }

      result = result.add(instance);
    }
    return result;
  }

  public static <T> @Nullable T instantiate(@NotNull KeyedLazyInstance<T> bean) {
    try {
      return bean.getInstance();
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (ExtensionNotApplicableException ignore) {
      return null;
    }
    catch (Exception | LinkageError e) {
      LOG.error(e);
      return null;
    }
  }

  protected final @NotNull PersistentList<T> buildExtensions(@NotNull Set<String> keys) {
    List<KeyedLazyInstance<T>> extensions = getExtensions();
    synchronized (lock) {
      PersistentList<T> explicit = buildExtensionsFromExplicitRegistration(keys::contains);
      PersistentList<T> result = buildExtensionsFromExtensionPoint(bean -> keys.contains(bean.getKey()), extensions);
      return explicit.addAll(result);
    }
  }

  protected final @NotNull PersistentList<T> buildExtensionsFromExplicitRegistration(@NotNull Predicate<? super String> isMyBean) {
    PersistentList<T> result = persistentListOf();
    for (Map.Entry<String, PersistentList<T>> entry : explicitExtensions.entrySet()) {
      String key = entry.getKey();
      if (isMyBean.test(key)) {
        result = result.addAll(entry.getValue());
      }
    }
    return result;
  }

  @ApiStatus.Internal
  public final @Nullable ExtensionPoint<@NotNull KeyedLazyInstance<T>> getPoint() {
    //noinspection deprecation
    return Extensions.getRootArea().getExtensionPointIfRegistered(epName);
  }

  public boolean hasAnyExtensions() {
    synchronized (lock) {
      if (!explicitExtensions.isEmpty()) {
        return true;
      }
    }

    ExtensionPoint<@NotNull KeyedLazyInstance<T>> point = getPoint();
    return point != null && point.size() != 0;
  }

  public @NotNull String getName() {
    return epName;
  }

  @Override
  public long getModificationCount() {
    return tracker.getModificationCount();
  }

  protected void ensureValuesLoaded() {
    ExtensionPoint<@NotNull KeyedLazyInstance<T>> point = getPoint();
    if (point != null) {
      for (KeyedLazyInstance<T> bean : point.getExtensionList()) {
        bean.getInstance();
      }
    }
  }

  private final class MyExtensionPointListener implements ExtensionPointAndAreaListener<KeyedLazyInstance<T>>, ExtensionPointPriorityListener {
    @Override
    public void extensionAdded(@NotNull KeyedLazyInstance<T> bean, @NotNull PluginDescriptor pluginDescriptor) {
      if (bean.getKey() == null) {
        throw new PluginException("No key specified for extension of class " + bean.getInstance().getClass(),
                                  pluginDescriptor.getPluginId());
      }
      invalidateCacheForExtension(bean.getKey());
    }

    @Override
    public void extensionRemoved(@NotNull KeyedLazyInstance<T> bean, @NotNull PluginDescriptor pluginDescriptor) {
      invalidateCacheForExtension(bean.getKey());
    }

    @Override
    public void areaReplaced(@NotNull ExtensionsArea area) {
      synchronized (lock) {
        cache = cache.clear();
        tracker.incModificationCount();
      }
    }
  }
}