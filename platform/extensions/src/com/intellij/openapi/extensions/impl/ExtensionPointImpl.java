// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl;

import com.intellij.diagnostic.ActivityCategory;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.util.ArrayFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@ApiStatus.Internal
public abstract class ExtensionPointImpl<@NotNull T> implements ExtensionPoint<T>, Iterable<T> {
  static final Logger LOG = Logger.getInstance(ExtensionPointImpl.class);

  // test-only
  // guarded by this
  private static Set<ExtensionPointImpl<?>> POINTS_IN_READONLY_MODE;

  private static final ArrayFactory<ExtensionPointListener<?>> LISTENER_ARRAY_FACTORY = n -> n == 0 ? ExtensionPointListener.emptyArray() : new ExtensionPointListener[n];

  private final String name;
  private final String className;

  // immutable list, never modified inplace, only swapped atomically
  private volatile List<T> cachedExtensions;
  // Since JDK 9 Arrays.ArrayList.toArray() doesn't return T[] array (https://bugs.openjdk.java.net/browse/JDK-6260652),
  // but instead returns Object[], so, we cannot use toArray() anymore.
  // Only array.clone should be used because of performance reasons (https://youtrack.jetbrains.com/issue/IDEA-198172).
  private volatile T @Nullable [] cachedExtensionsAsArray;

  private final ComponentManager componentManager;

  protected final @NotNull PluginDescriptor pluginDescriptor;

  // guarded by this
  private volatile @NotNull List<ExtensionComponentAdapter> adapters = Collections.emptyList();
  private volatile boolean adaptersAreSorted = true;

  // guarded by this
  private ExtensionPointListener<T> @NotNull [] listeners = ExtensionPointListener.emptyArray();

  private @Nullable Class<T> extensionClass;

  private final boolean isDynamic;

  private final AtomicReference<ConcurrentMap<?, Map<?, ?>>> keyMapperToCacheRef = new AtomicReference<>();

  ExtensionPointImpl(@NotNull String name,
                     @NotNull String className,
                     @NotNull PluginDescriptor pluginDescriptor,
                     @NotNull ComponentManager componentManager,
                     @Nullable Class<T> extensionClass,
                     boolean dynamic) {
    this.name = name;
    this.className = className;
    this.pluginDescriptor = pluginDescriptor;
    this.componentManager = componentManager;
    this.extensionClass = extensionClass;
    isDynamic = dynamic;
  }

  final <@NotNull CACHE_KEY, @NotNull V> @NotNull ConcurrentMap<@NotNull CACHE_KEY, V> getCacheMap() {
    ConcurrentMap<?, ?> keyMapperToCache = keyMapperToCacheRef.get();
    if (keyMapperToCache == null) {
      keyMapperToCache = keyMapperToCacheRef.updateAndGet(prev -> prev == null ? new ConcurrentHashMap<>() : prev);
    }
    //noinspection unchecked
    return (ConcurrentMap<CACHE_KEY, V>)keyMapperToCache;
  }

  public final @NotNull String getName() {
    return name;
  }

  @Override
  public final  @NotNull String getClassName() {
    return className;
  }

  @Override
  public final boolean isDynamic() {
    return isDynamic;
  }

  @Override
  public final void registerExtension(@NotNull T extension, @NotNull LoadingOrder order) {
    doRegisterExtension(extension, order, getPluginDescriptor(), null);
  }

  @Override
  public final void registerExtension(@NotNull T extension, @NotNull Disposable parentDisposable) {
    registerExtension(extension, getPluginDescriptor(), parentDisposable);
  }

  @Override
  public final void registerExtension(@NotNull T extension,
                                      @NotNull PluginDescriptor pluginDescriptor, @NotNull Disposable parentDisposable) {
    doRegisterExtension(extension, LoadingOrder.ANY, pluginDescriptor, parentDisposable);
  }

  @Override
  public final @NotNull PluginDescriptor getPluginDescriptor() {
    return pluginDescriptor;
  }

  @Override
  public final void registerExtension(@NotNull T extension, @NotNull LoadingOrder order, @NotNull Disposable parentDisposable) {
    doRegisterExtension(extension, order, getPluginDescriptor(), parentDisposable);
  }

  public final ComponentManager getComponentManager() {
    return componentManager;
  }

  private synchronized void doRegisterExtension(@NotNull T extension,
                                                @NotNull LoadingOrder order,
                                                @NotNull PluginDescriptor pluginDescriptor,
                                                @Nullable Disposable parentDisposable) {
    assertNotReadOnlyMode();
    checkExtensionType(extension, getExtensionClass(), null);

    for (ExtensionComponentAdapter adapter : adapters) {
      if (adapter instanceof ObjectComponentAdapter && ((ObjectComponentAdapter<?>)adapter).componentInstance == extension) {
        LOG.error("Extension was already added: " + extension);
        return;
      }
    }

    ObjectComponentAdapter<T> adapter = new ObjectComponentAdapter<>(extension, pluginDescriptor, order);
    addExtensionAdapter(adapter);
    notifyListeners(false, Collections.singletonList(adapter), listeners);

    if (parentDisposable != null) {
      Disposer.register(parentDisposable, () -> {
        synchronized (this) {
          int index = ContainerUtil.indexOfIdentity(adapters, adapter);
          if (index < 0) {
            LOG.error("Extension to be removed not found: " + adapter.componentInstance);
          }

          List<ExtensionComponentAdapter> list = new ArrayList<>(adapters);
          list.remove(index);
          adapters = list;
          clearCache();
          notifyListeners(true, Collections.singletonList(adapter), listeners);
        }
      });
    }
  }

  /**
   * There are valid cases where we need to register a lot of extensions programmatically,
   * e.g. see SqlDialectTemplateRegistrar, so, special method for bulk insertion is introduced.
   */
  public final void registerExtensions(@NotNull List<? extends T> extensions) {
    for (ExtensionComponentAdapter adapter : adapters) {
      if (adapter instanceof ObjectComponentAdapter) {
        if (ContainerUtil.containsIdentity(extensions, adapter)) {
          LOG.error("Extension was already added: " + ((ObjectComponentAdapter<?>)adapter).componentInstance);
          return;
        }
      }
    }

    List<ExtensionComponentAdapter> newAdapters = doRegisterExtensions(extensions);
    // do not call notifyListeners under lock
    ExtensionPointListener<T>[] listeners;
    synchronized (this) {
      listeners = this.listeners;
    }
    notifyListeners(false, newAdapters, listeners);
  }

  private synchronized @NotNull List<ExtensionComponentAdapter> doRegisterExtensions(@NotNull List<? extends T> extensions) {
    List<ExtensionComponentAdapter> newAdapters = new ArrayList<>(extensions.size());
    for (T extension : extensions) {
      newAdapters.add(new ObjectComponentAdapter<>(extension, getPluginDescriptor(), LoadingOrder.ANY));
    }

    if (adapters == Collections.<ExtensionComponentAdapter>emptyList()) {
      adapters = newAdapters;
    }
    else {
      List<ExtensionComponentAdapter> list = new ArrayList<>(adapters.size() + newAdapters.size());
      list.addAll(adapters);
      list.addAll(findInsertionIndexForAnyOrder(adapters), newAdapters);
      adapters = list;
    }
    clearCache();
    return newAdapters;
  }

  private static int findInsertionIndexForAnyOrder(@NotNull List<? extends ExtensionComponentAdapter> adapters) {
    int index = adapters.size();
    while (index > 0) {
      ExtensionComponentAdapter lastAdapter = adapters.get(index - 1);
      if (lastAdapter.getOrder() == LoadingOrder.LAST) {
        index--;
      }
      else {
        break;
      }
    }
    return index;
  }

  private void checkExtensionType(@NotNull T extension, @NotNull Class<T> extensionClass, @Nullable ExtensionComponentAdapter adapter) {
    if (!extensionClass.isInstance(extension)) {
      @NonNls String message = "Extension " + extension.getClass().getName() + " does not implement " + extensionClass;
      if (adapter == null) {
        throw new RuntimeException(message);
      }
      else {
        message += " (adapter=" + adapter + ")";
        throw componentManager.createError(message, null, adapter.getPluginDescriptor().getPluginId(),
                                           Collections.singletonMap("threadDump", ThreadDumper.dumpThreadsToString()));
      }
    }
  }

  @Override
  public final @NotNull List<T> getExtensionList() {
    List<T> result = cachedExtensions;
    return result == null ? computeExtensionList() : result;
  }

  private synchronized @NotNull List<T> computeExtensionList() {
    List<T> result = cachedExtensions;
    if (result == null) {
      T[] array = processAdapters();
      cachedExtensionsAsArray = array;
      result = array.length == 0 ? Collections.emptyList() : ContainerUtil.immutableList(array);
      cachedExtensions = result;
    }
    return result;
  }

  @Override
  public final T @NotNull [] getExtensions() {
    T[] array = cachedExtensionsAsArray;
    if (array == null) {
      synchronized (this) {
        array = cachedExtensionsAsArray;
        if (array == null) {
          cachedExtensionsAsArray = array = processAdapters();
          cachedExtensions = array.length == 0 ? Collections.emptyList() : ContainerUtil.immutableList(array);
        }
      }
    }
    return array.length == 0 ? array : array.clone();
  }

  /**
   * Do not use it if there is any extension point listener, because in this case behaviour is not predictable -
   * events will be fired during iteration and probably it will be not expected.
   * <p>
   * Use only for interface extension points, not for bean.
   * <p>
   * Due to internal reasons, there is no easy way to implement hasNext in a reliable manner,
   * so, `next` may return `null` (in this case stop iteration).
   */
  @Override
  @ApiStatus.Experimental
  public final @NotNull Iterator<T> iterator() {
    List<T> result = cachedExtensions;
    return result == null ? createIterator() : result.iterator();
  }

  public final void processWithPluginDescriptor(boolean shouldBeSorted, @NotNull BiConsumer<? super T, ? super PluginDescriptor> consumer) {
    if (isInReadOnlyMode()) {
      for (T extension : cachedExtensions) {
        consumer.accept(extension, pluginDescriptor /* doesn't matter for tests */);
      }
      return;
    }

    for (ExtensionComponentAdapter adapter : shouldBeSorted ? getThreadSafeAdapterList(true) : adapters) {
      T extension = processAdapter(adapter);
      if (extension != null) {
        consumer.accept(extension, adapter.getPluginDescriptor());
      }
    }
  }

  public final void processImplementations(boolean shouldBeSorted, @NotNull BiConsumer<? super Supplier<? extends @Nullable T>, ? super PluginDescriptor> consumer) {
    if (isInReadOnlyMode()) {
      for (T extension : cachedExtensions) {
        consumer.accept((Supplier<T>)() -> extension, pluginDescriptor /* doesn't matter for tests */);
      }
      return;
    }

    // do not use getThreadSafeAdapterList - no need to check that no listeners, because processImplementations is not a generic-purpose method
    for (ExtensionComponentAdapter adapter : shouldBeSorted ? getSortedAdapters() : adapters) {
      consumer.accept((Supplier<T>)() -> adapter.createInstance(componentManager), adapter.getPluginDescriptor());
    }
  }

  @TestOnly
  public final void checkImplementations(@NotNull Consumer<? super ExtensionComponentAdapter> consumer) {
    for (ExtensionComponentAdapter adapter : getSortedAdapters()) {
      consumer.accept(adapter);
    }
  }

  // null id means that instance was created and extension element cleared
  public final void processIdentifiableImplementations(@NotNull BiConsumer<@NotNull Supplier<@Nullable T>, @Nullable String> consumer) {
    // do not use getThreadSafeAdapterList - no need to check that no listeners, because processImplementations is not a generic-purpose method
    for (ExtensionComponentAdapter adapter : getSortedAdapters()) {
      consumer.accept(() -> adapter.createInstance(componentManager), adapter.getOrderId());
    }
  }

  private synchronized @NotNull List<ExtensionComponentAdapter> getThreadSafeAdapterList(boolean failIfListenerAdded) {
    CHECK_CANCELED.run();

    if (!isDynamic && listeners.length > 0) {
      String message = "Listeners not allowed for extension point " + getName();
      if (failIfListenerAdded) {
        LOG.error(message);
      }
      else {
        LOG.warn(message);
        getExtensionList();
      }
    }

    return getSortedAdapters();
  }

  private @NotNull Iterator<T> createIterator() {
    int size;
    List<ExtensionComponentAdapter> adapters = getThreadSafeAdapterList(true);
    size = adapters.size();
    if (size == 0) {
      return Collections.emptyIterator();
    }

    return new Iterator<T>() {
      private int currentIndex;

      @Override
      public boolean hasNext() {
        return currentIndex < size;
      }

      @Override
      public @Nullable T next() {
        do {
          T extension = processAdapter(adapters.get(currentIndex++));
          if (extension != null) {
            return extension;
          }
        }
        while (hasNext());
        return null;
      }
    };
  }

  @Override
  public final Spliterator<T> spliterator() {
    return Spliterators.spliterator(iterator(), size(), Spliterator.IMMUTABLE | Spliterator.NONNULL | Spliterator.DISTINCT);
  }

  @Override
  public final @NotNull Stream<T> extensions() {
    List<T> result = cachedExtensions;
    return result == null ? StreamSupport.stream(spliterator(), false) : result.stream();
  }

  @Override
  public final int size() {
    List<T> cache = cachedExtensions;
    return cache == null ? adapters.size() : cache.size();
  }

  public final @NotNull List<ExtensionComponentAdapter> getSortedAdapters() {
    if (adaptersAreSorted) {
      return adapters;
    }

    synchronized (this) {
      if (adaptersAreSorted) {
        return adapters;
      }

      if (adapters.size() > 1) {
        List<ExtensionComponentAdapter> list = new ArrayList<>(adapters);
        LoadingOrder.sort(list);
        adapters = list;
      }
      adaptersAreSorted = true;
    }
    return adapters;
  }

  private T @NotNull [] processAdapters() {
    assertNotReadOnlyMode();

    // check before to avoid any "restore" work if already cancelled
    CHECK_CANCELED.run();

    long startTime = StartUpMeasurer.getCurrentTime();

    List<ExtensionComponentAdapter> adapters = getSortedAdapters();
    int totalSize = adapters.size();
    Class<T> extensionClass = getExtensionClass();
    T[] result = ArrayUtil.newArray(extensionClass, totalSize);
    if (totalSize == 0) {
      return result;
    }

    Set<T> duplicates = this instanceof BeanExtensionPoint ? null : CollectionFactory.createSmallMemoryFootprintSet(totalSize);
    ExtensionPointListener<T>[] listeners = this.listeners;
    int extensionIndex = 0;
    for (int i = 0; i < adapters.size(); i++) {
      T extension = processAdapter(adapters.get(i), listeners, result, duplicates, extensionClass, adapters);
      if (extension != null) {
        result[extensionIndex++] = extension;
      }
    }

    if (extensionIndex != result.length) {
      result = Arrays.copyOf(result, extensionIndex);
    }

    // don't count ProcessCanceledException as valid action to measure (later special category can be introduced if needed)
    ActivityCategory category = componentManager.getActivityCategory(true);
    StartUpMeasurer.addCompletedActivity(startTime, extensionClass, category, /* pluginId = */ null, StartUpMeasurer.MEASURE_THRESHOLD);
    return result;
  }

  // This method needs to be synchronized because XmlExtensionAdapter.createInstance takes a lock on itself, and if it's called without
  // EP lock and tries to add an EP listener, we can get a deadlock because of lock ordering violation
  // (EP->adapter in one thread, adapter->EP in the other thread)
  private synchronized @Nullable T processAdapter(@NotNull ExtensionComponentAdapter adapter) {
    try {
      if (!checkThatClassloaderIsActive(adapter)) {
        return null;
      }
      T instance = adapter.createInstance(componentManager);
      if (instance == null && LOG.isDebugEnabled()) {
        LOG.debug(adapter + " not loaded because it reported that not applicable");
      }
      return instance;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
    }
    return null;
  }

  private @Nullable T processAdapter(@NotNull ExtensionComponentAdapter adapter,
                                     ExtensionPointListener<T> @Nullable [] listeners,
                                     T @Nullable [] result,
                                     @Nullable Set<T> duplicates,
                                     @NotNull Class<T> extensionClassForCheck,
                                     @NotNull List<? extends ExtensionComponentAdapter> adapters) {
    try {
      if (!checkThatClassloaderIsActive(adapter)) {
        return null;
      }

      boolean isNotifyThatAdded = listeners != null && listeners.length != 0 && !adapter.isInstanceCreated() && !isDynamic;
      // do not call CHECK_CANCELED here in loop because it is called by createInstance()
      T extension = adapter.createInstance(componentManager);
      if (extension == null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(adapter + " not loaded because it reported that not applicable");
        }
        return null;
      }

      if (duplicates != null && !duplicates.add(extension)) {
        T duplicate = ContainerUtil.find(duplicates, d -> d.equals(extension));
        assert result != null;

        LOG.error("Duplicate extension found:\n" +
                  "                   " + extension + ";\n" +
                  "  prev extension:  " + duplicate + ";\n" +
                  "  adapter:         " + adapter + ";\n" +
                  "  extension class: " + extensionClassForCheck + ";\n" +
                  "  result:          " + Arrays.asList(result) + ";\n" +
                  "  adapters:        " + adapters
        );
      }
      else {
        checkExtensionType(extension, extensionClassForCheck, adapter);
        if (isNotifyThatAdded) {
          notifyListeners(false, Collections.singletonList(adapter), listeners);
        }
        return extension;
      }
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
    }
    return null;
  }

  private static boolean checkThatClassloaderIsActive(@NotNull ExtensionComponentAdapter adapter) {
    ClassLoader classLoader = adapter.getPluginDescriptor().getPluginClassLoader();
    if (classLoader instanceof PluginAwareClassLoader &&
        ((PluginAwareClassLoader)classLoader).getState() != PluginAwareClassLoader.ACTIVE) {
      LOG.warn(adapter + " not loaded because classloader is being unloaded");
      return false;
    }
    return true;
  }

  /**
   * Put extension point in read-only mode and replace existing extensions by supplied.
   * For tests this method is more preferable than {@link #registerExtension)} because makes registration more isolated and strict
   * (no one can modify extension point until `parentDisposable` is not disposed).
   * <p>
   * Please use {@link com.intellij.testFramework.ExtensionTestUtil#maskExtensions(ExtensionPointName, List, Disposable)} instead of direct usage.
   */
  @TestOnly
  @ApiStatus.Internal
  public final synchronized void maskAll(@NotNull List<? extends T> newList, @NotNull Disposable parentDisposable, boolean fireEvents) {
    if (POINTS_IN_READONLY_MODE == null) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      POINTS_IN_READONLY_MODE = Collections.newSetFromMap(new IdentityHashMap<>());
    }
    else {
      assertNotReadOnlyMode();
    }

    List<T> oldList = cachedExtensions;
    T[] oldArray = cachedExtensionsAsArray;

    cachedExtensions = ContainerUtil.immutableList(newList);
    //noinspection unchecked
    cachedExtensionsAsArray = newList.toArray((T[])Array.newInstance(getExtensionClass(), 0));
    POINTS_IN_READONLY_MODE.add(this);

    ExtensionPointListener<T>[] listeners = this.listeners;
    if (fireEvents && listeners.length > 0) {
      if (oldList != null) {
        doNotifyListeners(true, oldList, listeners);
      }
      doNotifyListeners(false, newList, this.listeners);
    }

    clearUserCache();

    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        synchronized (this) {
          POINTS_IN_READONLY_MODE.remove(ExtensionPointImpl.this);
          cachedExtensions = oldList;
          cachedExtensionsAsArray = oldArray;

          ExtensionPointListener<T>[] listeners = ExtensionPointImpl.this.listeners;
          if (fireEvents && listeners.length > 0) {
            doNotifyListeners(true, newList, listeners);
            if (oldList != null) {
              doNotifyListeners(false, oldList, listeners);
            }
          }

          clearUserCache();
        }
      }
    });
  }

  @TestOnly
  private void doNotifyListeners(boolean isRemoved, @NotNull List<? extends T> extensions, @NotNull ExtensionPointListener<T> @NotNull [] listeners) {
    for (ExtensionPointListener<T> listener : listeners) {
      if (listener instanceof ExtensionPointAdapter) {
        try {
          ((ExtensionPointAdapter<T>)listener).extensionListChanged();
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
      else {
        for (T extension : extensions) {
          try {
            if (isRemoved) {
              listener.extensionRemoved(extension, pluginDescriptor);
            }
            else {
              listener.extensionAdded(extension, pluginDescriptor);
            }
          }
          catch (ProcessCanceledException e) {
            throw e;
          }
          catch (Throwable e) {
            LOG.error(e);
          }
        }
      }
    }
  }

  @Override
  public final synchronized void unregisterExtensions(@NotNull Predicate<? super T> filter) {
    getExtensionList();
    unregisterExtensions((clsName, adapter) -> {
      T extension = adapter.createInstance(componentManager);
      return !filter.test(extension);
    }, false);
  }

  @Override
  public final synchronized void unregisterExtension(@NotNull T extension) {
    if (!unregisterExtensions((__, adapter) -> !adapter.isInstanceCreated() || adapter.createInstance(componentManager) != extension, true)) {
      // there is a possible case that particular extension was replaced in particular environment, e.g. Upsource
      // replaces some IntelliJ extensions (important for CoreApplicationEnvironment), so, just log as error instead of throw error
      LOG.warn("Extension to be removed not found: " + extension);
    }
  }

  @Override
  public final void unregisterExtension(@NotNull Class<? extends T> extensionClass) {
    String classNameToUnregister = extensionClass.getCanonicalName();
    if (!unregisterExtensions((cls, adapter) -> !cls.equals(classNameToUnregister), /* stopAfterFirstMatch = */ true)) {
      LOG.warn("Extension to be removed not found: " + extensionClass);
    }
  }

  @Override
  public final boolean unregisterExtensions(@NotNull BiPredicate<? super String, ? super ExtensionComponentAdapter> extensionClassFilter,
                                            boolean stopAfterFirstMatch) {
    List<Runnable> listenerCallbacks = new ArrayList<>();
    List<Runnable> priorityListenerCallbacks = new ArrayList<>();
    boolean result = unregisterExtensions(adapter -> extensionClassFilter.test(adapter.getAssignableToClassName(), adapter), stopAfterFirstMatch, priorityListenerCallbacks, listenerCallbacks);
    for (Runnable callback : priorityListenerCallbacks) {
      callback.run();
    }
    for (Runnable callback : listenerCallbacks) {
      callback.run();
    }
    return result;
  }

  /**
   * Unregisters extensions for which the specified predicate returns false and collects the runnables for listener invocation into the given list
   * so that listeners can be called later.
   */
  final synchronized boolean unregisterExtensions(@NotNull Predicate<? super ExtensionComponentAdapter> extensionClassFilter,
                                                  boolean stopAfterFirstMatch,
                                                  @NotNull List<Runnable> priorityListenerCallbacks,
                                                  @NotNull List<Runnable> listenerCallbacks) {
    ExtensionPointListener<T>[] listeners = this.listeners;
    List<ExtensionComponentAdapter> removedAdapters = null;
    List<ExtensionComponentAdapter> adapters = this.adapters;
    for (int i = adapters.size() - 1; i >= 0; i--) {
      ExtensionComponentAdapter adapter = adapters.get(i);
      if (extensionClassFilter.test(adapter)) {
        continue;
      }

      if (adapters == this.adapters) {
        adapters = new ArrayList<>(adapters);
      }
      adapters.remove(i);

      if (listeners.length != 0) {
        if (removedAdapters == null) {
          removedAdapters = new ArrayList<>();
        }
        removedAdapters.add(adapter);
      }

      if (stopAfterFirstMatch) {
        break;
      }
    }

    if (adapters == this.adapters) {
      return false;
    }

    clearCache();
    this.adapters = adapters;

    if (removedAdapters == null) {
      return true;
    }

    List<ExtensionPointListener<T>> priorityListeners = ContainerUtil.filter(listeners, listener -> listener instanceof ExtensionPointPriorityListener);
    List<ExtensionPointListener<T>> regularListeners = ContainerUtil.filter(listeners, listener -> !(listener instanceof ExtensionPointPriorityListener));

    List<ExtensionComponentAdapter> finalRemovedAdapters = removedAdapters;
    if (!priorityListeners.isEmpty()) {
      priorityListenerCallbacks.add(() ->
        notifyListeners(true, finalRemovedAdapters, priorityListeners.toArray(ExtensionPointListener.emptyArray()))
      );
    }
    if (!regularListeners.isEmpty()) {
      listenerCallbacks.add(() ->
        notifyListeners(true, finalRemovedAdapters, regularListeners.toArray(ExtensionPointListener.emptyArray()))
      );
    }
    return true;
  }

  abstract void unregisterExtensions(@NotNull ComponentManager componentManager,
                                     @NotNull PluginDescriptor pluginDescriptor,
                                     @NotNull List<ExtensionDescriptor> elements,
                                     @NotNull List<Runnable> priorityListenerCallbacks,
                                     @NotNull List<Runnable> listenerCallbacks);

  private void notifyListeners(boolean isRemoved,
                               @NotNull List<? extends ExtensionComponentAdapter> adapters,
                               @NotNull ExtensionPointListener<T> @NotNull [] listeners) {
    for (ExtensionPointListener<T> listener : listeners) {
      if (listener instanceof ExtensionPointAdapter) {
        try {
          ((ExtensionPointAdapter<T>)listener).extensionListChanged();
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
      else {
        for (ExtensionComponentAdapter adapter : adapters) {
          if (isRemoved && !adapter.isInstanceCreated()) {
            continue;
          }

          try {
            T extension = adapter.createInstance(componentManager);
            if (extension != null) {
              if (isRemoved) {
                listener.extensionRemoved(extension, adapter.getPluginDescriptor());
              }
              else {
                listener.extensionAdded(extension, adapter.getPluginDescriptor());
              }
            }
          }
          catch (ProcessCanceledException e) {
            throw e;
          }
          catch (Throwable e) {
            LOG.error(e);
          }
        }
      }
    }
  }

  @Override
  public final void addExtensionPointListener(@NotNull ExtensionPointListener<T> listener,
                                              boolean invokeForLoadedExtensions,
                                              @Nullable Disposable parentDisposable) {
    boolean isAdded = addListener(listener);
    if (!isAdded) {
      return;
    }

    if (invokeForLoadedExtensions) {
      //noinspection unchecked
      notifyListeners(false, getSortedAdapters(), new ExtensionPointListener[]{listener});
    }

    if (parentDisposable != null) {
      Disposer.register(parentDisposable, () -> removeExtensionPointListener(listener));
    }
  }

  private static @NotNull <T> ArrayFactory<ExtensionPointListener<T>> listenerArrayFactory() {
    //noinspection unchecked,rawtypes
    return (ArrayFactory)LISTENER_ARRAY_FACTORY;
  }

  // true if added
  private synchronized boolean addListener(@NotNull ExtensionPointListener<T> listener) {
    if (ArrayUtilRt.indexOf(listeners, listener, 0, listeners.length) != -1) {
      return false;
    }
    if (listener instanceof ExtensionPointPriorityListener) {
      listeners = ArrayUtil.prepend(listener, listeners, listenerArrayFactory());
    }
    else {
      listeners = ArrayUtil.append(listeners, listener, listenerArrayFactory());
    }
    return true;
  }

  @Override
  public final void addExtensionPointListener(@NotNull ExtensionPointChangeListener listener,
                                              boolean invokeForLoadedExtensions,
                                              @Nullable Disposable parentDisposable) {
    addExtensionPointListener(new ExtensionPointAdapter<T>() {
      @Override
      public void extensionListChanged() {
        listener.extensionListChanged();
      }
    }, invokeForLoadedExtensions, parentDisposable);
  }

  @Override
  public synchronized final void addChangeListener(@NotNull Runnable listener, @Nullable Disposable parentDisposable) {
    ExtensionPointAdapter<T> listenerAdapter = new ExtensionPointAdapter<T>() {
      @Override
      public void extensionListChanged() {
        listener.run();
      }
    };

    listeners = ArrayUtil.append(listeners, listenerAdapter, listenerArrayFactory());
    if (parentDisposable != null) {
      Disposer.register(parentDisposable, () -> removeExtensionPointListener(listenerAdapter));
    }
  }

  @Override
  public final synchronized void removeExtensionPointListener(@NotNull ExtensionPointListener<T> listener) {
    listeners = ArrayUtil.remove(listeners, listener, listenerArrayFactory());
  }

  public final synchronized void reset() {
    List<ExtensionComponentAdapter> adapters = this.adapters;
    this.adapters = Collections.emptyList();
    // clear cache before notify listeners to ensure that listeners don't get outdated data
    clearCache();
    if (!adapters.isEmpty() && listeners.length > 0) {
      notifyListeners(true, adapters, listeners);
    }

    // help GC
    listeners = ExtensionPointListener.emptyArray();
    extensionClass = null;
  }

  public final @NotNull Class<T> getExtensionClass() {
    Class<T> extensionClass = this.extensionClass;
    if (extensionClass == null) {
      try {
        extensionClass = componentManager.loadClass(className, pluginDescriptor);
        this.extensionClass = extensionClass;
      }
      catch (ClassNotFoundException e) {
        throw componentManager.createError(e, pluginDescriptor.getPluginId());
      }
    }
    return extensionClass;
  }

  @Override
  public final String toString() {
    return getName();
  }

  // private, internal only for tests
  final synchronized void addExtensionAdapter(@NotNull ExtensionComponentAdapter adapter) {
    List<ExtensionComponentAdapter> list = new ArrayList<>(adapters.size() + 1);
    list.addAll(adapters);
    list.add(adapter);
    adapters = list;
    clearCache();
  }

  final void clearUserCache() {
    ConcurrentMap<?, Map<?, ?>> map = keyMapperToCacheRef.get();
    if (map != null) {
      map.clear();
    }
  }

  private void clearCache() {
    cachedExtensions = null;
    cachedExtensionsAsArray = null;
    adaptersAreSorted = false;
    clearUserCache();

    // asserted here because clearCache is called on any write action
    assertNotReadOnlyMode();
  }

  private void assertNotReadOnlyMode() {
    if (isInReadOnlyMode()) {
      throw new IllegalStateException(this + " in a read-only mode and cannot be modified");
    }
  }

  abstract @NotNull ExtensionComponentAdapter createAdapter(@NotNull ExtensionDescriptor extensionElement,
                                                            @NotNull PluginDescriptor pluginDescriptor,
                                                            @NotNull ComponentManager componentManager);

  /**
   * {@link #clearCache} is not called.
   *
   * myAdapters is modified directly without copying - method must be called only during start-up.
   */
  public final synchronized void registerExtensions(@NotNull List<ExtensionDescriptor> extensionElements,
                                                    @NotNull PluginDescriptor pluginDescriptor,
                                                    @Nullable List<Runnable> listenerCallbacks) {
    List<ExtensionComponentAdapter> adapters = this.adapters;
    if (adapters == Collections.<ExtensionComponentAdapter>emptyList()) {
      adapters = new ArrayList<>(extensionElements.size());
      this.adapters = adapters;
      adaptersAreSorted = false;
    }
    else {
      ((ArrayList<ExtensionComponentAdapter>)adapters).ensureCapacity(adapters.size() + extensionElements.size());
    }

    int oldSize = adapters.size();
    for (ExtensionDescriptor extensionElement : extensionElements) {
      if (extensionElement.os == null || componentManager.isSuitableForOs(extensionElement.os)) {
        adapters.add(createAdapter(extensionElement, pluginDescriptor, componentManager));
      }
    }
    int newSize = adapters.size();

    clearCache();
    ExtensionPointListener<T>[] listeners = this.listeners;
    if (listenerCallbacks == null || listeners.length == 0) {
      return;
    }

    List<ExtensionComponentAdapter> addedAdapters = Collections.emptyList();
    for (ExtensionPointListener<T> listener : listeners) {
      if (!(listener instanceof ExtensionPointAdapter)) {
        // must be reported in order
        List<ExtensionComponentAdapter> newlyAddedUnsortedList = adapters.subList(oldSize, newSize);
        Set<ExtensionComponentAdapter> newlyAddedSet = Collections.newSetFromMap(new IdentityHashMap<>(newlyAddedUnsortedList.size()));
        newlyAddedSet.addAll(newlyAddedUnsortedList);
        addedAdapters = new ArrayList<>(newlyAddedSet.size());
        for (ExtensionComponentAdapter adapter : getSortedAdapters()) {
          if (newlyAddedSet.contains(adapter)) {
            addedAdapters.add(adapter);
          }
        }
        break;
      }
    }

    List<ExtensionComponentAdapter> finalAddedAdapters = addedAdapters;
    listenerCallbacks.add(() -> notifyListeners(false, finalAddedAdapters, listeners));
  }

  @TestOnly
  final synchronized void notifyAreaReplaced(@NotNull ExtensionsArea oldArea) {
    for (ExtensionPointListener<T> listener : listeners) {
      if (listener instanceof ExtensionPointAndAreaListener) {
        ((ExtensionPointAndAreaListener<?>)listener).areaReplaced(oldArea);
      }
    }
  }

  public final @Nullable <V extends T> V findExtension(@NotNull Class<V> aClass, boolean isRequired, @NotNull ThreeState strictMatch) {
    if (strictMatch != ThreeState.NO) {
      @SuppressWarnings("unchecked")
      V result = (V)findExtensionByExactClass(aClass);
      if (result != null) {
        return result;
      }
      else if (strictMatch == ThreeState.YES) {
        return null;
      }
    }

    List<T> extensionsCache = cachedExtensions;
    if (extensionsCache == null) {
      for (ExtensionComponentAdapter adapter : getThreadSafeAdapterList(false)) {
        // findExtension is called for a lot of extension point - do not fail if listeners were added (e.g. FacetTypeRegistryImpl)
        try {
          if (aClass.isAssignableFrom(adapter.getImplementationClass(componentManager))) {
            //noinspection unchecked
            return (V)processAdapter(adapter);
          }
        }
        catch (ClassNotFoundException e) {
          componentManager.logError(e, adapter.getPluginDescriptor().getPluginId());
        }
      }
    }
    else {
      for (T extension : extensionsCache) {
        if (aClass.isInstance(extension)) {
          //noinspection unchecked
          return (V)extension;
        }
      }
    }

    if (isRequired) {
      @NonNls String message = "cannot find extension implementation " + aClass + "(epName=" + getName() + ", extensionCount=" + size();
      List<T> cache = cachedExtensions;
      if (cache != null) {
        message += ", cachedExtensions";
      }
      if (isInReadOnlyMode()) {
        message += ", point in read-only mode";
      }
      message += ")";
      throw componentManager.createError(message, getPluginDescriptor().getPluginId());
    }
    return null;
  }

  private @Nullable T findExtensionByExactClass(@NotNull Class<? extends T> aClass) {
    List<T> cachedExtensions = this.cachedExtensions;
    if (cachedExtensions == null) {
      for (ExtensionComponentAdapter adapter : getThreadSafeAdapterList(false)) {
        Object classOrName = adapter.implementationClassOrName;
        if (classOrName instanceof String ? classOrName.equals(aClass.getName()) : classOrName == aClass) {
          return processAdapter(adapter);
        }
      }
    }
    else {
      for (T extension : cachedExtensions) {
        if (aClass == extension.getClass()) {
          return extension;
        }
      }
    }

    return null;
  }

  private static final class ObjectComponentAdapter<T> extends ExtensionComponentAdapter {
    private final @NotNull T componentInstance;

    private ObjectComponentAdapter(@NotNull T extension,
                                   @NotNull PluginDescriptor pluginDescriptor,
                                   @NotNull LoadingOrder loadingOrder) {
      super(extension.getClass().getName(), pluginDescriptor, null, loadingOrder, (componentManager1, adapter) -> extension.getClass());

      componentInstance = extension;
    }

    @Override
    boolean isInstanceCreated() {
      return true;
    }

    @Override
    public @NotNull <I> I createInstance(@Nullable ComponentManager componentManager) {
      //noinspection unchecked
      return (I)componentInstance;
    }
  }

  private synchronized boolean isInReadOnlyMode() {
    return POINTS_IN_READONLY_MODE != null && POINTS_IN_READONLY_MODE.contains(this);
  }

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private static Runnable CHECK_CANCELED = EmptyRunnable.getInstance();

  public static void setCheckCanceledAction(@NotNull Runnable checkCanceled) {
    CHECK_CANCELED = () -> {
      try {
        checkCanceled.run();
      }
      catch (ProcessCanceledException e) {
        // otherwise ExceptionInInitializerError happens and the class is screwed forever
        if (!isInsideClassInitializer(e.getStackTrace())) {
          throw e;
        }
      }
    };
  }

  private static boolean isInsideClassInitializer(StackTraceElement @NotNull [] trace) {
    //noinspection SpellCheckingInspection
    return ContainerUtil.exists(trace, s -> "<clinit>".equals(s.getMethodName()));
  }
}
