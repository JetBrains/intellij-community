// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.diagnostic.ActivityCategory;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
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
import com.intellij.util.pico.DefaultPicoContainer;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@SuppressWarnings("SynchronizeOnThis")
@ApiStatus.Internal
public abstract class ExtensionPointImpl<@NotNull T> implements ExtensionPoint<T>, Iterable<T> {
  private static final ExtensionPointListener<?>[] EMPTY_ARRAY = new ExtensionPointListener<?>[0];

  static final Logger LOG = Logger.getInstance(ExtensionPointImpl.class);

  // test-only
  private static Set<ExtensionPointImpl<?>> POINTS_IN_READONLY_MODE; // guarded by this

  private static final ArrayFactory<ExtensionPointListener<?>> LISTENER_ARRAY_FACTORY =
    n -> n == 0 ? EMPTY_ARRAY : new ExtensionPointListener[n];

  private final String myName;
  private final String myClassName;

  private volatile List<T> myExtensionsCache; // immutable list, never modified inplace, only swapped atomically
  // Since JDK 9 Arrays.ArrayList.toArray() doesn't return T[] array (https://bugs.openjdk.java.net/browse/JDK-6260652),
  // but instead returns Object[], so, we cannot use toArray() anymore.
  // Only array.clone should be used because of performance reasons (https://youtrack.jetbrains.com/issue/IDEA-198172).
  private volatile T @Nullable [] myExtensionsCacheAsArray;

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private ComponentManager componentManager;

  protected final @NotNull PluginDescriptor pluginDescriptor;

  // guarded by this
  private volatile @NotNull List<ExtensionComponentAdapter> myAdapters = Collections.emptyList();
  private volatile boolean adaptersIsSorted = true;

  // guarded by this
  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized", "unchecked"})
  private ExtensionPointListener<T> @NotNull [] myListeners = (ExtensionPointListener<T>[])EMPTY_ARRAY;

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private @Nullable Class<T> myExtensionClass;

  private final boolean isDynamic;

  private final AtomicReference<ConcurrentMap<?, Map<?, ?>>> keyMapperToCacheRef = new AtomicReference<>();

  ExtensionPointImpl(@NotNull String name,
                     @NotNull String className,
                     @NotNull PluginDescriptor pluginDescriptor,
                     @Nullable Class<T> extensionClass,
                     boolean dynamic) {
    myName = name;
    myClassName = className;
    this.pluginDescriptor = pluginDescriptor;
    myExtensionClass = extensionClass;
    isDynamic = dynamic;
  }

  final void setComponentManager(@NotNull ComponentManager value) {
    componentManager = value;
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
    return myName;
  }

  @Override
  public final  @NotNull String getClassName() {
    return myClassName;
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

  private synchronized void doRegisterExtension(@NotNull T extension, @NotNull LoadingOrder order,
                                                @NotNull PluginDescriptor pluginDescriptor, @Nullable Disposable parentDisposable) {
    assertNotReadOnlyMode();
    checkExtensionType(extension, getExtensionClass(), null);

    for (ExtensionComponentAdapter adapter : myAdapters) {
      if (adapter instanceof ObjectComponentAdapter && ((ObjectComponentAdapter<?>)adapter).componentInstance == extension) {
        LOG.error("Extension was already added: " + extension);
        return;
      }
    }

    ObjectComponentAdapter<T> adapter = new ObjectComponentAdapter<>(extension, pluginDescriptor, order);
    addExtensionAdapter(adapter);
    notifyListeners(false, Collections.singletonList(adapter), myListeners);

    if (parentDisposable != null) {
      Disposer.register(parentDisposable, () -> {
        synchronized (this) {
          int index = ContainerUtil.indexOfIdentity(myAdapters, adapter);
          if (index < 0) {
            LOG.error("Extension to be removed not found: " + adapter.componentInstance);
          }

          List<ExtensionComponentAdapter> list = new ArrayList<>(myAdapters);
          list.remove(index);
          myAdapters = list;
          clearCache();
          notifyListeners(true, Collections.singletonList(adapter), myListeners);
        }
      });
    }
  }

  /**
   * There are valid cases where we need to register a lot of extensions programmatically,
   * e.g. see SqlDialectTemplateRegistrar, so, special method for bulk insertion is introduced.
   */
  public final synchronized void registerExtensions(@NotNull List<? extends T> extensions) {
    for (ExtensionComponentAdapter adapter : myAdapters) {
      if (adapter instanceof ObjectComponentAdapter) {
        if (ContainerUtil.containsIdentity(extensions, adapter)) {
          LOG.error("Extension was already added: " + ((ObjectComponentAdapter<?>)adapter).componentInstance);
          return;
        }
      }
    }

    List<ExtensionComponentAdapter> newAdapters = new ArrayList<>(extensions.size());
    for (T extension : extensions) {
      newAdapters.add(new ObjectComponentAdapter<>(extension, getPluginDescriptor(), LoadingOrder.ANY));
    }

    if (myAdapters == Collections.<ExtensionComponentAdapter>emptyList()) {
      myAdapters = newAdapters;
    }
    else {
      List<ExtensionComponentAdapter> list = new ArrayList<>(myAdapters.size() + newAdapters.size());
      list.addAll(myAdapters);
      list.addAll(findInsertionIndexForAnyOrder(myAdapters), newAdapters);
      myAdapters = list;
    }
    clearCache();

    notifyListeners(false, newAdapters, myListeners);
  }

  private static int findInsertionIndexForAnyOrder(@NotNull List<ExtensionComponentAdapter> adapters) {
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
      String message = "Extension " + extension.getClass() + " does not implement " + extensionClass;
      if (adapter != null) {
        message += " (adapter=" + adapter + ")";
      }
      throw new ExtensionException(message, extension.getClass());
    }
  }

  @Override
  public final @NotNull List<T> getExtensionList() {
    List<T> result = myExtensionsCache;
    return result != null ? result : calcExtensionList();
  }

  private synchronized List<T> calcExtensionList() {
    List<T> result = myExtensionsCache;
    if (result == null) {
      T[] array = processAdapters();
      myExtensionsCacheAsArray = array;
      result = array.length == 0 ? Collections.emptyList() : ContainerUtil.immutableList(array);
      myExtensionsCache = result;
    }
    return result;
  }

  @Override
  public final T @NotNull [] getExtensions() {
    T[] array = myExtensionsCacheAsArray;
    if (array == null) {
      synchronized (this) {
        array = myExtensionsCacheAsArray;
        if (array == null) {
          myExtensionsCacheAsArray = array = processAdapters();
          myExtensionsCache = array.length == 0 ? Collections.emptyList() : ContainerUtil.immutableList(array);
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
    List<T> result = myExtensionsCache;
    return result == null ? createIterator() : result.iterator();
  }

  public final void processWithPluginDescriptor(boolean shouldBeSorted, @NotNull BiConsumer<? super T, ? super PluginDescriptor> consumer) {
    if (isInReadOnlyMode()) {
      for (T extension : myExtensionsCache) {
        consumer.accept(extension, pluginDescriptor /* doesn't matter for tests */);
      }
      return;
    }

    for (ExtensionComponentAdapter adapter : (shouldBeSorted ? getThreadSafeAdapterList(true) : myAdapters)) {
      T extension = processAdapter(adapter);
      if (extension != null) {
        consumer.accept(extension, adapter.getPluginDescriptor());
      }
    }
  }

  public final void processImplementations(boolean shouldBeSorted, @NotNull BiConsumer<Supplier<T>, ? super PluginDescriptor> consumer) {
    if (isInReadOnlyMode()) {
      for (T extension : myExtensionsCache) {
        consumer.accept(() -> extension, pluginDescriptor /* doesn't matter for tests */);
      }
      return;
    }

    // do not use getThreadSafeAdapterList - no need to check that no listeners, because processImplementations is not a generic-purpose method
    for (ExtensionComponentAdapter adapter : (shouldBeSorted ? getSortedAdapters() : myAdapters)) {
      consumer.accept(() -> adapter.createInstance(componentManager), adapter.getPluginDescriptor());
    }
  }

  // null id means that instance was created and extension element cleared
  public final void processIdentifiableImplementations(@NotNull BiConsumer<@NotNull Supplier<T>, @Nullable String> consumer) {
    // do not use getThreadSafeAdapterList - no need to check that no listeners, because processImplementations is not a generic-purpose method
    for (ExtensionComponentAdapter adapter : getSortedAdapters()) {
      String id = adapter.getOrderId();
      // https://github.com/JetBrains/kotlin/pull/3522
      if (id == null && "org.jetbrains.kotlin.idea.roots.KotlinNonJvmSourceRootConverterProvider".equals(adapter.getAssignableToClassName())) {
        id = "kotlin-non-jvm-source-roots";
      }
      consumer.accept(() -> adapter.createInstance(componentManager), id);
    }
  }

  private @NotNull List<ExtensionComponentAdapter> getThreadSafeAdapterList(boolean failIfListenerAdded) {
    CHECK_CANCELED.run();

    if (!isDynamic && myListeners.length > 0) {
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
    List<T> result = myExtensionsCache;
    return result == null ? StreamSupport.stream(spliterator(), false) : result.stream();
  }

  @Override
  public final int size() {
    List<? extends T> cache = myExtensionsCache;
    return cache == null ? myAdapters.size() : cache.size();
  }

  private @NotNull List<ExtensionComponentAdapter> getSortedAdapters() {
    if (adaptersIsSorted) {
      return myAdapters;
    }

    synchronized (this) {
      if (adaptersIsSorted) {
        return myAdapters;
      }

      if (myAdapters.size() > 1) {
        List<ExtensionComponentAdapter> list = new ArrayList<>(myAdapters);
        LoadingOrder.sort(list);
        myAdapters = list;
      }
      adaptersIsSorted = true;
    }
    return myAdapters;
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
    ExtensionPointListener<T>[] listeners = myListeners;
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
    ActivityCategory category = getActivityCategory((DefaultPicoContainer)componentManager.getPicoContainer());
    StartUpMeasurer.addCompletedActivity(startTime, extensionClass, category, /* pluginId = */ null, StartUpMeasurer.MEASURE_THRESHOLD);
    return result;
  }

  public abstract @NotNull ExtensionPointImpl<T> cloneFor(@NotNull ComponentManager manager);

  private static @NotNull ActivityCategory getActivityCategory(@NotNull DefaultPicoContainer picoContainer) {
    DefaultPicoContainer parent = picoContainer.getParent();
    if (parent == null) {
      return ActivityCategory.APP_EXTENSION;
    }
    if (parent.getParent() == null) {
      return ActivityCategory.PROJECT_EXTENSION;
    }
    return ActivityCategory.MODULE_EXTENSION;
  }

  // This method needs to be synchronized because XmlExtensionAdapter.createInstance takes a lock on itself, and if it's called without
  // EP lock and tries to add an EP listener, we can get a deadlock because of lock ordering violation
  // (EP->adapter in one thread, adapter->EP in the other thread)
  private synchronized @Nullable T processAdapter(@NotNull ExtensionComponentAdapter adapter) {
    try {
      return adapter.createInstance(componentManager);
    }
    catch (ExtensionNotApplicableException ignore) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(adapter + " not loaded because it reported that not applicable");
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

  private @Nullable T processAdapter(@NotNull ExtensionComponentAdapter adapter,
                                     ExtensionPointListener<T> @Nullable [] listeners,
                                     T @Nullable [] result,
                                     @Nullable Set<T> duplicates,
                                     @NotNull Class<T> extensionClassForCheck,
                                     @NotNull List<? extends ExtensionComponentAdapter> adapters) {
    try {
      boolean isNotifyThatAdded = listeners != null && listeners.length != 0 && !adapter.isInstanceCreated() && !isDynamic;
      // do not call CHECK_CANCELED here in loop because it is called by createInstance()
      T extension = adapter.createInstance(componentManager);
      if (duplicates != null && !duplicates.add(extension)) {
        T duplicate = duplicates.stream().filter(d->d.equals(extension)).findFirst().orElse(null);
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
    catch (ExtensionNotApplicableException ignore) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(adapter + " not loaded because it reported that not applicable");
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

  // used in upsource
  // remove extensions for which implementation class is not available
  @SuppressWarnings("unused")
  public final synchronized void removeUnloadableExtensions() {
    List<ExtensionComponentAdapter> adapters = myAdapters;
    for (int i = adapters.size() - 1; i >= 0; i--) {
      ExtensionComponentAdapter adapter = adapters.get(i);
      try {
        adapter.getImplementationClass();
      }
      catch (Throwable e) {
        if (adapters == myAdapters) {
          adapters = new ArrayList<>(adapters);
        }
        adapters.remove(i);
        clearCache();
      }
    }

    myAdapters = adapters;
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
  public final synchronized void maskAll(@NotNull List<T> list, @NotNull Disposable parentDisposable, boolean fireEvents) {
    if (POINTS_IN_READONLY_MODE == null) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      POINTS_IN_READONLY_MODE = Collections.newSetFromMap(new IdentityHashMap<>());
    }
    else {
      assertNotReadOnlyMode();
    }

    List<T> oldList = myExtensionsCache;
    T[] oldArray = myExtensionsCacheAsArray;

    myExtensionsCache = ContainerUtil.immutableList(list);
    myExtensionsCacheAsArray = list.toArray(ArrayUtil.newArray(getExtensionClass(), 0));
    POINTS_IN_READONLY_MODE.add(this);

    ExtensionPointListener<T>[] listeners = myListeners;
    if (fireEvents && listeners.length > 0) {
      if (oldList != null) {
        doNotifyListeners(true, oldList, listeners);
      }
      doNotifyListeners(false, list, myListeners);
    }

    clearUserCache();

    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        synchronized (this) {
          POINTS_IN_READONLY_MODE.remove(ExtensionPointImpl.this);
          myExtensionsCache = oldList;
          myExtensionsCacheAsArray = oldArray;

          ExtensionPointListener<T>[] listeners = myListeners;
          if (fireEvents && listeners.length > 0) {
            doNotifyListeners(true, list, listeners);
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
  private void doNotifyListeners(boolean isRemoved, @NotNull List<T> extensions, @NotNull ExtensionPointListener<T> @NotNull [] listeners) {
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
    if (!unregisterExtensions((className, adapter) -> {
      return !adapter.isInstanceCreated() || adapter.createInstance(componentManager) != extension;
    }, true)) {
      // there is a possible case that particular extension was replaced in particular environment, e.g. Upsource
      // replaces some IntelliJ extensions (important for CoreApplicationEnvironment), so, just log as error instead of throw error
      LOG.warn("Extension to be removed not found: " + extension);
    }
  }

  @Override
  public final void unregisterExtension(@NotNull Class<? extends T> extensionClass) {
    String classNameToUnregister = extensionClass.getCanonicalName();
    if (!unregisterExtensions((className, adapter) -> !className.equals(classNameToUnregister), /* stopAfterFirstMatch = */ true)) {
      LOG.warn("Extension to be removed not found: " + extensionClass);
    }
  }

  @Override
  public final boolean unregisterExtensions(@NotNull BiPredicate<? super String, ? super ExtensionComponentAdapter> extensionClassFilter,
                                            boolean stopAfterFirstMatch) {
    List<Runnable> listenerCallbacks = new ArrayList<>();
    List<Runnable> priorityListenerCallbacks = new ArrayList<>();
    boolean result = unregisterExtensions(extensionClassFilter, stopAfterFirstMatch, priorityListenerCallbacks, listenerCallbacks);
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
  final synchronized boolean unregisterExtensions(@NotNull BiPredicate<? super String, ? super ExtensionComponentAdapter> extensionClassFilter,
                                                  boolean stopAfterFirstMatch,
                                                  @NotNull List<Runnable> priorityListenerCallbacks,
                                                  @NotNull List<Runnable> listenerCallbacks) {
    boolean found = false;
    ExtensionPointListener<T>[] listeners = myListeners;
    List<ExtensionComponentAdapter> removedAdapters = null;
    List<ExtensionComponentAdapter> adapters = myAdapters;
    for (int i = adapters.size() - 1; i >= 0; i--) {
      ExtensionComponentAdapter adapter = adapters.get(i);
      if (extensionClassFilter.test(adapter.getAssignableToClassName(), adapter)) {
        continue;
      }

      clearCache();
      if (adapters == myAdapters) {
        adapters = new ArrayList<>(adapters);
      }
      adapters.remove(i);

      if (listeners.length != 0) {
        if (removedAdapters == null) {
          removedAdapters = new ArrayList<>();
        }
        removedAdapters.add(adapter);
      }

      found = true;
      if (stopAfterFirstMatch) {
        break;
      }
    }

    myAdapters = adapters;

    if (removedAdapters != null) {
      List<ExtensionComponentAdapter> finalRemovedAdapters = removedAdapters;

      List<ExtensionPointListener<T>> priorityListeners =
        ContainerUtil.filter(listeners, (listener) -> listener instanceof ExtensionPointPriorityListener);
      List<ExtensionPointListener<T>> regularListeners =
        ContainerUtil.filter(listeners, (listener) -> !(listener instanceof ExtensionPointPriorityListener));

      if (!priorityListeners.isEmpty()) {
        priorityListenerCallbacks.add(() -> notifyListeners(true, finalRemovedAdapters,
                                                            priorityListeners.toArray(new ExtensionPointListener[priorityListeners.size()])));
      }
      if (!regularListeners.isEmpty()) {
        listenerCallbacks.add(() -> notifyListeners(true, finalRemovedAdapters,
                                                    regularListeners.toArray(new ExtensionPointListener[regularListeners.size()])));
      }
    }
    return found;
  }

  abstract void unregisterExtensions(@NotNull ComponentManager componentManager,
                                     @NotNull PluginDescriptor pluginDescriptor,
                                     @NotNull List<Element> elements,
                                     @NotNull List<Runnable> priorityListenerCallbacks,
                                     @NotNull List<Runnable> listenerCallbacks);

  private void notifyListeners(boolean isRemoved,
                               @NotNull List<ExtensionComponentAdapter> adapters,
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

          T extension = adapter.createInstance(componentManager);
          try {
            if (isRemoved) {
              listener.extensionRemoved(extension, adapter.getPluginDescriptor());
            }
            else {
              listener.extensionAdded(extension, adapter.getPluginDescriptor());
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
  public final synchronized void addExtensionPointListener(@NotNull ExtensionPointListener<T> listener,
                                                           boolean invokeForLoadedExtensions,
                                                           @Nullable Disposable parentDisposable) {
    boolean isAdded = addListener(listener);
    if (isAdded && invokeForLoadedExtensions) {
      //noinspection unchecked
      notifyListeners(false, myAdapters, new ExtensionPointListener[]{listener});
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
  private boolean addListener(@NotNull ExtensionPointListener<T> listener) {
    if (ArrayUtilRt.indexOf(myListeners, listener, 0, myListeners.length) != -1) {
      return false;
    }
    if (listener instanceof ExtensionPointPriorityListener) {
      myListeners = ArrayUtil.prepend(listener, myListeners, listenerArrayFactory());
    }
    else {
      myListeners = ArrayUtil.append(myListeners, listener, listenerArrayFactory());
    }
    return true;
  }

  @Override
  public final void addExtensionPointListener(@NotNull ExtensionPointListener<T> listener) {
    addExtensionPointListener(listener, true, null);
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
  public final void addChangeListener(@NotNull Runnable listener, @Nullable Disposable parentDisposable) {
    ExtensionPointAdapter<T> listenerAdapter = new ExtensionPointAdapter<T>() {
      @Override
      public void extensionListChanged() {
        listener.run();
      }
    };

    myListeners = ArrayUtil.append(myListeners, listenerAdapter, listenerArrayFactory());
    if (parentDisposable != null) {
      Disposer.register(parentDisposable, () -> removeExtensionPointListener(listenerAdapter));
    }
  }

  @Override
  public final synchronized void removeExtensionPointListener(@NotNull ExtensionPointListener<T> listener) {
    myListeners = ArrayUtil.remove(myListeners, listener, listenerArrayFactory());
  }

  public final synchronized void reset() {
    List<ExtensionComponentAdapter> adapters = myAdapters;
    myAdapters = Collections.emptyList();
    // clear cache before notify listeners to ensure that listeners don't get outdated data
    clearCache();
    if (!adapters.isEmpty() && myListeners.length > 0) {
      notifyListeners(true, adapters, myListeners);
    }

    // help GC
    //noinspection unchecked
    myListeners = (ExtensionPointListener<T>[])EMPTY_ARRAY;
    myExtensionClass = null;
  }

  public final @NotNull Class<T> getExtensionClass() {
    Class<T> extensionClass = myExtensionClass;
    if (extensionClass == null) {
      try {
        ClassLoader pluginClassLoader = pluginDescriptor.getPluginClassLoader();
        //noinspection unchecked
        extensionClass = (Class<T>)(pluginClassLoader == null ? Class.forName(myClassName) : Class.forName(myClassName, true, pluginClassLoader));
        myExtensionClass = extensionClass;
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
    List<ExtensionComponentAdapter> list = new ArrayList<>(myAdapters.size() + 1);
    list.addAll(myAdapters);
    list.add(adapter);
    myAdapters = list;
    clearCache();
  }

  final void clearUserCache() {
    ConcurrentMap<?, Map<?, ?>> map = keyMapperToCacheRef.get();
    if (map != null) {
      map.clear();
    }
  }

  private void clearCache() {
    myExtensionsCache = null;
    myExtensionsCacheAsArray = null;
    adaptersIsSorted = false;
    clearUserCache();

    // asserted here because clearCache is called on any write action
    assertNotReadOnlyMode();
  }

  private void assertNotReadOnlyMode() {
    if (isInReadOnlyMode()) {
      throw new IllegalStateException(this + " in a read-only mode and cannot be modified");
    }
  }

  protected abstract @NotNull ExtensionComponentAdapter createAdapterAndRegisterInPicoContainerIfNeeded(@NotNull Element extensionElement,
                                                                                                        @NotNull PluginDescriptor pluginDescriptor,
                                                                                                        @NotNull ComponentManager componentManager);

  final synchronized void createAndRegisterAdapter(@NotNull Element extensionElement,
                                             @NotNull PluginDescriptor pluginDescriptor,
                                             @NotNull ComponentManager componentManager) {
    addExtensionAdapter(createAdapterAndRegisterInPicoContainerIfNeeded(extensionElement, pluginDescriptor, componentManager));
  }

  /**
   * {@link #clearCache} is not called.
   *
   * myAdapters is modified directly without copying - method must be called only during start-up.
   */
  final synchronized void registerExtensions(@NotNull List<Element> extensionElements,
                                             @NotNull IdeaPluginDescriptor pluginDescriptor,
                                             @NotNull ComponentManager componentManager,
                                             @Nullable List<Runnable> listenerCallbacks) {
    if (this.componentManager != componentManager) {
      LOG.error("The same point on different levels (pointName=" + getName() + ")");
    }

    List<ExtensionComponentAdapter> adapters = myAdapters;
    if (adapters == Collections.<ExtensionComponentAdapter>emptyList()) {
      adapters = new ArrayList<>(extensionElements.size());
      myAdapters = adapters;
      adaptersIsSorted = false;
    }
    else {
      ((ArrayList<ExtensionComponentAdapter>)adapters).ensureCapacity(adapters.size() + extensionElements.size());
    }

    int oldSize = adapters.size();
    for (Element extensionElement : extensionElements) {
      adapters.add(createAdapterAndRegisterInPicoContainerIfNeeded(extensionElement, pluginDescriptor, componentManager));
    }
    int newSize = adapters.size();

    clearCache();
    ExtensionPointListener<T>[] listeners = myListeners;
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
    listenerCallbacks.add(() -> {
      notifyListeners(false, finalAddedAdapters, listeners);
    });
  }

  @TestOnly
  final synchronized void notifyAreaReplaced(@NotNull ExtensionsArea oldArea) {
    for (ExtensionPointListener<T> listener : myListeners) {
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

    List<? extends T> extensionsCache = myExtensionsCache;
    if (extensionsCache == null) {
      for (ExtensionComponentAdapter adapter : getThreadSafeAdapterList(false)) {
        // findExtension is called for a lot of extension point - do not fail if listeners were added (e.g. FacetTypeRegistryImpl)
        try {
          if (aClass.isAssignableFrom(adapter.getImplementationClass())) {
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
      String message = "could not find extension implementation " + aClass;
      if (isInReadOnlyMode()) {
        message += " (point in read-only mode)";
      }
      throw new IllegalArgumentException(message);
    }
    return null;
  }

  private @Nullable T findExtensionByExactClass(@NotNull Class<? extends T> aClass) {
    List<? extends T> extensionsCache = myExtensionsCache;
    if (extensionsCache == null) {
      for (ExtensionComponentAdapter adapter : getThreadSafeAdapterList(false)) {
        Object classOrName = adapter.myImplementationClassOrName;
        if (classOrName instanceof String ? classOrName.equals(aClass.getName()) : classOrName == aClass) {
          return processAdapter(adapter);
        }
      }
    }
    else {
      for (T extension : extensionsCache) {
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
      super(extension.getClass().getName(), pluginDescriptor, null, loadingOrder);

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
        if (!isInsideClassInitializer(
          e.getStackTrace())) { // otherwise ExceptionInInitializerError happens and the class is screwed forever
          throw e;
        }
      }
    };
  }

  private static boolean isInsideClassInitializer(StackTraceElement @NotNull [] trace) {
    //noinspection SpellCheckingInspection
    return Arrays.stream(trace).anyMatch(s -> "<clinit>".equals(s.getMethodName()));
  }
}
