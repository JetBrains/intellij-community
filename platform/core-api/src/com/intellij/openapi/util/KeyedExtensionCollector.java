// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointAndAreaListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ExtensionPointPriorityListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.TechnologyFilter;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.PluginAwareLazyExtension;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Java11Shim;
import kotlinx.collections.immutable.PersistentList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  /** The extensions of a key with their modules, for {@link #forKey(Object, Project)}. It is filled and cleared as {@link #cache}. */
  private volatile @UnmodifiableView Map<String, List<ExtensionWithModule<T>>> cacheWithModules = Java11Shim.INSTANCE.mapOf();
  private final @NotNull String epName;
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
      cacheWithModules = Collections.emptyMap();
      tracker.incModificationCount();
    }
  }

  private void addExtensionPointListener(@NotNull ExtensionPoint<@NotNull KeyedLazyInstance<T>> point) {
    if (myEpListenerAdded.compareAndSet(false, true)) {
      point.addExtensionPointListener(new MyExtensionPointListener(), false, null);
    }
  }

  protected void invalidateCacheForExtension(@NotNull String key) {
    if (!cache.containsKey(key) && !cacheWithModules.containsKey(key)) {
      return;
    }

    synchronized (lock) {
      cache = without(cache, key);
      cacheWithModules = without(cacheWithModules, key);
      tracker.incModificationCount();
    }
  }

  public void addExplicitExtension(@NotNull KeyT key, @NotNull T t) {
    String stringKey = keyToString(key);
    synchronized (lock) {
      PersistentList<T> value = explicitExtensions.get(stringKey);
      explicitExtensions = with(explicitExtensions, stringKey, value == null ? persistentListOf(t) : value.adding(t));
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
        list = list.removing(t);
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

    if (!myEpListenerAdded.get()) return cached;

    synchronized (lock) {
      List<T> recent = cache.get(stringKey);
      if (recent != null) {
        return recent;
      }

      cache = with(cache, stringKey, cached);
      return cached;
    }
  }

  /**
   * The extensions of {@link #forKey(Object)} that apply to {@code project}, see {@link TechnologyFilter}.
   * A subclass that overrides {@link #buildExtensions(String, Object)} must also override {@link #buildExtensionsWithModules(String, Object)}.
   * Otherwise the two lookups give different extensions.
   */
  @ApiStatus.Experimental
  public final @NotNull @Unmodifiable List<T> forKey(@NotNull KeyT key, @NotNull Project project) {
    TechnologyFilter filter = TechnologyFilter.getInstance();
    if (!filter.isRestricted(project)) {
      return forKey(key);
    }
    return ContainerUtil.mapNotNull(forKeyWithModules(key), extension -> extension.isApplicable(filter, project) ? extension.getInstance() : null);
  }

  private @NotNull @Unmodifiable List<ExtensionWithModule<T>> forKeyWithModules(@NotNull KeyT key) {
    String stringKey = keyToString(key);

    List<ExtensionWithModule<T>> cached = cacheWithModules.get(stringKey);
    if (cached != null) {
      return cached;
    }

    cached = buildExtensionsWithModules(stringKey, key);

    if (!myEpListenerAdded.get()) return cached;

    synchronized (lock) {
      List<ExtensionWithModule<T>> recent = cacheWithModules.get(stringKey);
      if (recent != null) {
        return recent;
      }

      cacheWithModules = with(cacheWithModules, stringKey, cached);
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
      return explicit == null ? result : explicit.addingAll(result);
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
        String key = keyOf(bean);
        return key != null && keys.contains(key);
      }, extensions);
      return toPersistentList(explicit).addingAll(result);
    }
  }

  /**
   * The extensions of a key with their modules, see {@link ExtensionWithModule}.
   * They are the extensions of {@link #buildExtensions(String, Object)}, in the same order.
   * A subclass that overrides that method to look the extensions up by other keys overrides this one in the same way.
   */
  @ApiStatus.Internal
  protected @NotNull @Unmodifiable List<ExtensionWithModule<T>> buildExtensionsWithModules(@NotNull String stringKey, @NotNull KeyT key) {
    return buildExtensionsWithModules(Collections.singleton(stringKey));
  }

  /** The same as {@link #buildExtensions(Set)}, with the module of each extension. */
  @ApiStatus.Internal
  protected final @NotNull @Unmodifiable List<ExtensionWithModule<T>> buildExtensionsWithModules(@NotNull @Unmodifiable Set<String> keys) {
    List<KeyedLazyInstance<T>> extensions = getExtensions();
    synchronized (lock) {
      List<T> explicit = buildExtensionsFromExplicitRegistration(keys::contains);
      List<ExtensionWithModule<T>> explicitWithModules = ContainerUtil.map(explicit, it -> new ExtensionWithModule<>(it, null));

      List<ExtensionWithModule<T>> resultWithModules = new SmartList<>();
      for (KeyedLazyInstance<T> bean : extensions) {
        String key = keyOf(bean);
        if (key == null || !keys.contains(key)) {
          continue;
        }
        T instance = instantiate(bean);
        if (instance != null) {
          resultWithModules.add(new ExtensionWithModule<>(instance, moduleOf(bean)));
        }
      }
      return toPersistentList(explicitWithModules).addingAll(resultWithModules);
    }
  }

  private static @Nullable String keyOf(@NotNull KeyedLazyInstance<?> bean) {
    try {
      return bean.getKey();
    }
    catch (IllegalStateException e) {
      LOG.error(e);
      return null;
    }
  }

  /** The module that declares a bean, or {@code null} for a bean that code created without a module. */
  private static @Nullable PluginDescriptor moduleOf(@NotNull KeyedLazyInstance<?> bean) {
    return bean instanceof PluginAwareLazyExtension ? ((PluginAwareLazyExtension)bean).getPluginDescriptor() : null;
  }

  /**
   * An extension of the collector with the module that declares its bean.
   * An explicit extension has no module, and it applies to every project.
   * For a collector that gives the extensions of a project, see {@code LanguageExtension.allForLanguage(Language, Project)}.
   */
  @ApiStatus.Internal
  public static final class ExtensionWithModule<T> {
    private final @NotNull T instance;
    private final @Nullable PluginDescriptor module;

    ExtensionWithModule(@NotNull T instance, @Nullable PluginDescriptor module) {
      this.instance = instance;
      this.module = module;
    }

    public @NotNull T getInstance() {
      return instance;
    }

    public @Nullable PluginDescriptor getModule() {
      return module;
    }

    /** Whether the extension applies to {@code project}. An extension without a module always applies. */
    public boolean isApplicable(@NotNull TechnologyFilter filter, @NotNull Project project) {
      return module == null || filter.isApplicable(module, project);
    }

    @Override
    public String toString() {
      return module == null ? instance.toString() : instance + " module=" + module.getPluginId();
    }
  }

  protected final @NotNull @Unmodifiable List<T> buildExtensionsFromExplicitRegistration(@NotNull Predicate<? super String> isMyBean) {
    PersistentList<T> result = persistentListOf();
    for (Map.Entry<String, PersistentList<T>> entry : explicitExtensions.entrySet()) {
      String key = entry.getKey();
      if (isMyBean.test(key)) {
        result = result.addingAll(entry.getValue());
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
        cacheWithModules = Java11Shim.INSTANCE.mapOf();
        myEpListenerAdded.set(false);
        tracker.incModificationCount();
      }
    }
  }
}