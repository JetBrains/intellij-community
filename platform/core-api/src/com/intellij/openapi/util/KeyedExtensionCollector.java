// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.Java11Shim;
import com.intellij.util.KeyedLazyInstance;
import kotlinx.collections.immutable.PersistentList;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static com.intellij.util.containers.UtilKt.with;
import static com.intellij.util.containers.UtilKt.without;
import static kotlinx.collections.immutable.ExtensionsKt.persistentListOf;
import static kotlinx.collections.immutable.ExtensionsKt.toPersistentList;

public class KeyedExtensionCollector<T, KeyT> implements ModificationTracker {
  private static final Logger LOG = Logger.getInstance(KeyedExtensionCollector.class);

  protected final @NonNls String lock;

  /** Guarded by {@link #lock} */
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private Map<String, PersistentList<T>> explicitExtensions = Java11Shim.INSTANCE.mapOf();

  private volatile @UnmodifiableView Map<String, List<T>> cache = Java11Shim.INSTANCE.mapOf();
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
      cache = Collections.emptyMap();
      tracker.incModificationCount();
    }
  }

  private void addExtensionPointListener(@NotNull ExtensionPoint<@NotNull KeyedLazyInstance<T>> point) {
    if (myEpListenerAdded.compareAndSet(false, true)) {
      point.addExtensionPointListener(new MyExtensionPointListener(), false, null);
    }
  }

  protected void invalidateCacheForExtension(@NotNull String key) {
    if (!cache.containsKey(key)) {
      return;
    }

    synchronized (lock) {
      cache = without(cache, key);
      tracker.incModificationCount();
    }
  }

  public void addExplicitExtension(@NotNull KeyT key, @NotNull T t) {
    String stringKey = keyToString(key);
    synchronized (lock) {
      PersistentList<T> value = explicitExtensions.get(stringKey);
      explicitExtensions = with(explicitExtensions, stringKey, value == null ? persistentListOf(t) : value.add(t));
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
        explicitExtensions = list.isEmpty() ? without(explicitExtensions, stringKey) : with(explicitExtensions, stringKey, list);
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
  public final @NotNull @Unmodifiable List<T> forKey(@NotNull KeyT key) {
    String stringKey = keyToString(key);

    List<T> cached = cache.get(stringKey);
    if (cached != null) {
      return cached;
    }

    cached = buildExtensions(stringKey, key);

    synchronized (lock) {
      List<T> recent = cache.get(stringKey);
      if (recent != null) {
        return recent;
      }

      cache = with(cache, stringKey, cached);
      return cached;
    }
  }

  public final @UnknownNullability T findSingle(@NotNull KeyT key) {
    List<T> list = forKey(key);
    return list.isEmpty() ? null : list.get(0);
  }

  protected @NotNull @Unmodifiable List<T> buildExtensions(@NotNull String stringKey, @NotNull KeyT key) {
    // compute out of our lock (https://youtrack.jetbrains.com/issue/IDEA-208060)
    List<KeyedLazyInstance<T>> extensions = getExtensions();
    synchronized (lock) {
      PersistentList<T> explicit = explicitExtensions.get(stringKey);
      List<T> result = buildExtensionsFromExtensionPoint(bean -> stringKey.equals(bean.getKey()), extensions);
      return explicit == null ? result : explicit.addAll(result);
    }
  }

  // must be called not under our lock
  protected final @NotNull @Unmodifiable List<KeyedLazyInstance<T>> getExtensions() {
    ExtensionPoint<@NotNull KeyedLazyInstance<T>> point = getPoint();
    if (point == null) {
      return Java11Shim.INSTANCE.listOf();
    }
    else {
      addExtensionPointListener(point);
      return point.getExtensionList();
    }
  }

  final @NotNull @Unmodifiable List<T> buildExtensionsFromExtensionPoint(@NotNull Predicate<? super KeyedLazyInstance<T>> isMyBean,
                                                           @NotNull List<? extends KeyedLazyInstance<T>> extensions) {
    List<T> result = null;
    T r1 = null;
    T r2 = null;
    for (KeyedLazyInstance<T> bean : extensions) {
      if (!isMyBean.test(bean)) {
        continue;
      }

      T instance = instantiate(bean);
      if (instance == null) {
        continue;
      }

      if (result != null) {
        result.add(instance);
      }
      else if (r1 == null) {
        r1 = instance;
      }
      else if (r2 == null) {
        r2 = instance;
      }
      else {
        result = new ArrayList<>();
        result.add(r1);
        result.add(r2);
        result.add(instance);
      }
    }

    if (result != null) {
      return result;
    }
    else if (r2 != null) {
      return Java11Shim.INSTANCE.listOf(r1, r2);
    }
    else if (r1 != null) {
      return Java11Shim.INSTANCE.listOf(r1);
    }
    else {
      return Java11Shim.INSTANCE.listOf();
    }
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

  protected final @NotNull @Unmodifiable List<T> buildExtensions(@NotNull @Unmodifiable Set<String> keys) {
    List<KeyedLazyInstance<T>> extensions = getExtensions();
    synchronized (lock) {
      List<T> explicit = buildExtensionsFromExplicitRegistration(keys::contains);
      List<T> result = buildExtensionsFromExtensionPoint(bean -> {
        String key;
        try {
          key = bean.getKey();
        }
        catch (IllegalStateException e) {
          LOG.error(e);
          return false;
        }

        return keys.contains(key);
      }, extensions);
      return toPersistentList(explicit).addAll(result);
    }
  }

  protected final @NotNull @Unmodifiable List<T> buildExtensionsFromExplicitRegistration(@NotNull Predicate<? super String> isMyBean) {
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
        cache = Java11Shim.INSTANCE.mapOf();
        tracker.incModificationCount();
      }
    }
  }
}