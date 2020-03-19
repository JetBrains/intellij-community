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
import com.intellij.openapi.util.NotNullFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ArrayFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OpenTHashSet;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.PicoContainer;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

@SuppressWarnings("SynchronizeOnThis")
@ApiStatus.Internal
public abstract class ExtensionPointImpl<@NotNull T> implements ExtensionPoint<T>, Iterable<T> {
  static final Logger LOG = Logger.getInstance(ExtensionPointImpl.class);

  // test-only
  private static Set<ExtensionPointImpl<?>> POINTS_IN_READONLY_MODE; // guarded by this

  private static final ArrayFactory<ExtensionPointListener<?>> LISTENER_ARRAY_FACTORY =
    n -> n == 0 ? ExtensionPointListener.EMPTY_ARRAY : new ExtensionPointListener[n];

  private final String myName;
  private final String myClassName;

  private volatile List<T> myExtensionsCache; // immutable list, never modified inplace, only swapped atomically
  // Since JDK 9 Arrays.ArrayList.toArray() doesn't return T[] array (https://bugs.openjdk.java.net/browse/JDK-6260652),
  // but instead returns Object[], so, we cannot use toArray() anymore.
  // Only array.clone should be used because of performance reasons (https://youtrack.jetbrains.com/issue/IDEA-198172).
  private volatile T @Nullable [] myExtensionsCacheAsArray;

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private ComponentManager myComponentManager;

  protected final @NotNull PluginDescriptor myDescriptor;

  // guarded by this
  private volatile @NotNull List<ExtensionComponentAdapter> myAdapters = Collections.emptyList();
  private volatile boolean myAdaptersIsSorted = true;

  // guarded by this
  @SuppressWarnings({"unchecked", "FieldAccessedSynchronizedAndUnsynchronized"})
  private ExtensionPointListener<T> @NotNull [] myListeners = ExtensionPointListener.EMPTY_ARRAY;

  @Nullable Class<T> myExtensionClass;

  private final boolean myDynamic;

  ExtensionPointImpl(@NotNull String name,
                     @NotNull String className,
                     @NotNull PluginDescriptor pluginDescriptor,
                     boolean dynamic) {
    myName = name;
    myClassName = className;
    myDescriptor = pluginDescriptor;
    myDynamic = dynamic;
  }

  synchronized final void setComponentManager(@NotNull ComponentManager value) {
    myComponentManager = value;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getClassName() {
    return myClassName;
  }

  @Override
  public boolean isDynamic() {
    return myDynamic;
  }

  @Override
  public synchronized void registerExtension(@NotNull T extension) {
    doRegisterExtension(extension, LoadingOrder.ANY, null);
  }

  @Override
  public synchronized void registerExtension(@NotNull T extension, @NotNull LoadingOrder order) {
    doRegisterExtension(extension, order, null);
  }

  @Override
  public synchronized void registerExtension(@NotNull T extension, @NotNull Disposable parentDisposable) {
    doRegisterExtension(extension, LoadingOrder.ANY, parentDisposable);
  }

  @Override
  @NotNull
  public final PluginDescriptor getPluginDescriptor() {
    return myDescriptor;
  }

  @Override
  public synchronized void registerExtension(@NotNull T extension, @NotNull LoadingOrder order, @NotNull Disposable parentDisposable) {
    doRegisterExtension(extension, order, parentDisposable);
  }

  public synchronized ComponentManager getComponentManager() {
    return myComponentManager;
  }

  private synchronized void doRegisterExtension(@NotNull T extension, @NotNull LoadingOrder order, @Nullable Disposable parentDisposable) {
    assertNotReadOnlyMode();
    checkExtensionType(extension, getExtensionClass(), null);

    for (ExtensionComponentAdapter adapter : myAdapters) {
      if (adapter instanceof ObjectComponentAdapter && castComponentInstance(adapter) == extension) {
        LOG.error("Extension was already added: " + extension);
        return;
      }
    }

    ObjectComponentAdapter<T> adapter = new ObjectComponentAdapter<>(extension, getPluginDescriptor(), order);
    addExtensionAdapter(adapter);
    notifyListeners(ExtensionEvent.ADDED, extension, adapter.getPluginDescriptor(), myListeners);

    if (parentDisposable != null) {
      Disposer.register(parentDisposable, () -> {
        synchronized (this) {
          int index = ContainerUtil.indexOfIdentity(myAdapters, adapter);
          if (index < 0) {
            LOG.error("Extension to be removed not found: " + adapter.myComponentInstance);
          }

          List<ExtensionComponentAdapter> list = new ArrayList<>(myAdapters);
          list.remove(index);
          myAdapters = list;
          clearCache();
          notifyListeners(ExtensionEvent.REMOVED, adapter.myComponentInstance, adapter.getPluginDescriptor(), myListeners);
        }
      });
    }
  }

  /**
   * There are valid cases where we need to register a lot of extensions programmatically,
   * e.g. see SqlDialectTemplateRegistrar, so, special method for bulk insertion is introduced.
   */
  public synchronized void registerExtensions(@NotNull List<? extends T> extensions) {
    for (ExtensionComponentAdapter adapter : myAdapters) {
      if (adapter instanceof ObjectComponentAdapter) {
        Object instance = castComponentInstance(adapter);
        if (ContainerUtil.containsIdentity(extensions, instance)) {
          LOG.error("Extension was already added: " + instance);
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

    notifyListeners(ExtensionEvent.ADDED, newAdapters, myListeners);
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

  @NotNull
  @Override
  public List<T> getExtensionList() {
    List<T> result = myExtensionsCache;
    if (result == null) {
      synchronized (this) {
        result = myExtensionsCache;
        if (result == null) {
          T[] array = processAdapters();
          myExtensionsCacheAsArray = array;
          result = array.length == 0 ? Collections.emptyList() : ContainerUtil.immutableList(array);
          myExtensionsCache = result;
        }
      }
    }
    return result;
  }

  @Override
  public T @NotNull [] getExtensions() {
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
  @NotNull
  public final Iterator<T> iterator() {
    List<T> result = myExtensionsCache;
    return result == null ? createIterator() : result.iterator();
  }

  public final void processWithPluginDescriptor(boolean shouldBeSorted, @NotNull BiConsumer<? super T, ? super PluginDescriptor> consumer) {
    if (isInReadOnlyMode()) {
      for (T extension : myExtensionsCache) {
        consumer.accept(extension, myDescriptor /* doesn't matter for tests */);
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
    // do not use getThreadSafeAdapterList - no need to check that no listeners, because processImplementations is not a generic-purpose method
    for (ExtensionComponentAdapter adapter : (shouldBeSorted ? getSortedAdapters() : myAdapters)) {
      consumer.accept(() -> adapter.createInstance(myComponentManager), adapter.getPluginDescriptor());
    }
  }

  @NotNull
  private List<ExtensionComponentAdapter> getThreadSafeAdapterList(boolean failIfListenerAdded) {
    CHECK_CANCELED.run();

    if (!myDynamic && myListeners.length > 0) {
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

  @NotNull
  private Iterator<T> createIterator() {
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
      @Nullable
      public T next() {
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

  @NotNull
  @Override
  public Stream<T> extensions() {
    return getExtensionList().stream();
  }

  @Override
  public boolean hasAnyExtensions() {
    List<? extends T> cache = myExtensionsCache;
    return cache == null ? !myAdapters.isEmpty() : !cache.isEmpty();
  }

  private @NotNull List<ExtensionComponentAdapter> getSortedAdapters() {
    if (myAdaptersIsSorted) {
      return myAdapters;
    }

    synchronized (this) {
      if (myAdaptersIsSorted) {
        return myAdapters;
      }

      if (myAdapters.size() > 1) {
        List<ExtensionComponentAdapter> list = new ArrayList<>(myAdapters);
        LoadingOrder.sort(list);
        myAdapters = list;
      }
      myAdaptersIsSorted = true;
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

    OpenTHashSet<T> duplicates = this instanceof BeanExtensionPoint ? null : new OpenTHashSet<>(totalSize);

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
    ActivityCategory category = getActivityCategory(myComponentManager.getPicoContainer());
    StartUpMeasurer.addCompletedActivity(startTime, extensionClass, category, /* pluginId = */ null, StartUpMeasurer.MEASURE_THRESHOLD);
    return result;
  }

  @NotNull
  public abstract ExtensionPointImpl<T> cloneFor(@NotNull ComponentManager manager);

  @NotNull
  private static ActivityCategory getActivityCategory(@NotNull PicoContainer picoContainer) {
    PicoContainer parent = picoContainer.getParent();
    if (parent == null) {
      return ActivityCategory.APP_EXTENSION;
    }
    if (parent.getParent() == null) {
      return ActivityCategory.PROJECT_EXTENSION;
    }
    return ActivityCategory.MODULE_EXTENSION;
  }

  private T processAdapter(@NotNull ExtensionComponentAdapter adapter) {
    return processAdapter(adapter, null, null, null, null, null);
  }

  @Nullable
  private T processAdapter(@NotNull ExtensionComponentAdapter adapter,
                           ExtensionPointListener<T> @Nullable [] listeners,
                           T @Nullable [] result,
                           @Nullable OpenTHashSet<T> duplicates,
                           @Nullable Class<T> extensionClassForCheck,
                           @Nullable List<? extends ExtensionComponentAdapter> adapters) {
    try {
      boolean isNotifyThatAdded = listeners != null && listeners.length != 0 && !adapter.isInstanceCreated() && !myDynamic;
      // do not call CHECK_CANCELED here in loop because it is called by createInstance()
      T extension = adapter.createInstance(myComponentManager);
      if (duplicates != null && !duplicates.add(extension)) {
        T duplicate = duplicates.get(extension);
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
        if (extensionClassForCheck != null) {
          checkExtensionType(extension, extensionClassForCheck, adapter);
        }

        if (isNotifyThatAdded) {
          notifyListeners(ExtensionEvent.ADDED, extension, adapter.getPluginDescriptor(), listeners);
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
  public synchronized void removeUnloadableExtensions() {
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
  public synchronized void maskAll(@NotNull List<? extends T> list, @NotNull Disposable parentDisposable, boolean fireEvents) {
    if (POINTS_IN_READONLY_MODE == null) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      POINTS_IN_READONLY_MODE = ContainerUtil.newIdentityTroveSet();
    }
    else {
      assertNotReadOnlyMode();
    }

    List<T> oldList = myExtensionsCache;
    T[] oldArray = myExtensionsCacheAsArray;

    myExtensionsCache = new ArrayList<>(list);
    myExtensionsCacheAsArray = list.toArray(ArrayUtil.newArray(getExtensionClass(), 0));
    POINTS_IN_READONLY_MODE.add(this);

    if (fireEvents && myListeners.length > 0) {
      if (oldList != null) {
        notifyListeners(ExtensionEvent.REMOVED, () -> ContainerUtil.map(oldList, extension ->
          Pair.create(extension, getPluginDescriptor())), myListeners);
      }
      notifyListeners(ExtensionEvent.ADDED, () -> ContainerUtil.map(list, extension ->
        Pair.create(extension, getPluginDescriptor())), myListeners);
    }

    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        synchronized (this) {
          POINTS_IN_READONLY_MODE.remove(ExtensionPointImpl.this);
          myExtensionsCache = oldList;
          myExtensionsCacheAsArray = oldArray;

          if (fireEvents && myListeners.length > 0) {
            notifyListeners(ExtensionEvent.REMOVED, () -> ContainerUtil.map(list, extension ->
              Pair.create(extension, getPluginDescriptor())), myListeners);

            if (oldList != null) {
              notifyListeners(ExtensionEvent.ADDED, () -> ContainerUtil.map(oldList, extension ->
                Pair.create(extension, getPluginDescriptor())), myListeners);
            }
          }
        }
      }
    });
  }

  @Override
  public synchronized void unregisterExtensions(@NotNull Predicate<? super T> filter) {
    getExtensionList();
    unregisterExtensions((clsName, adapter) -> {
      T extension = adapter.createInstance(myComponentManager);
      return !filter.test(extension);
    }, false);
  }

  @Override
  public synchronized void unregisterExtension(@NotNull T extension) {
    if (!unregisterExtensions(
      (clsName, adapter) -> !adapter.isInstanceCreated()
                            || adapter.createInstance(myComponentManager) != extension,
      true)) {

      // there is a possible case that particular extension was replaced in particular environment, e.g. Upsource
      // replaces some IntelliJ extensions (important for CoreApplicationEnvironment), so, just log as error instead of throw error
      LOG.warn("Extension to be removed not found: " + extension);
    }
  }

  @Override
  public void unregisterExtension(@NotNull Class<? extends T> extensionClass) {
    String classNameToUnregister = extensionClass.getCanonicalName();
    if (!unregisterExtensions((className, adapter) -> !className.equals(classNameToUnregister), /* stopAfterFirstMatch = */ true)) {
      LOG.warn("Extension to be removed not found: " + extensionClass);
    }
  }

  @Override
  public boolean unregisterExtensions(@NotNull BiPredicate<? super String, ? super ExtensionComponentAdapter> extensionClassFilter,
                                      boolean stopAfterFirstMatch) {
    List<Runnable> listenerCallbacks = new ArrayList<>();
    boolean result = unregisterExtensions(extensionClassFilter, stopAfterFirstMatch, listenerCallbacks);
    for (Runnable callback : listenerCallbacks) {
      callback.run();
    }
    return result;
  }

  @Override
  public synchronized boolean unregisterExtensions(@NotNull BiPredicate<? super String, ? super ExtensionComponentAdapter> extensionClassFilter,
                                                   boolean stopAfterFirstMatch,
                                                   List<Runnable> listenerCallbacks) {
    boolean found = false;
    ExtensionPointListener<T>[] listeners = myListeners;
    List<ExtensionComponentAdapter> removedAdapters = listeners.length > 0 ? new SmartList<>() : null;
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
      if (removedAdapters != null) {
        removedAdapters.add(adapter);
      }
      found = true;
      if (stopAfterFirstMatch) {
        break;
      }
    }

    myAdapters = adapters;

    if (removedAdapters != null && !removedAdapters.isEmpty()) {
      listenerCallbacks.add(() -> notifyListeners(ExtensionEvent.REMOVED, removedAdapters, listeners));
    }
    return found;
  }

  public abstract void unregisterExtensions(@NotNull ComponentManager componentManager,
                                            @NotNull PluginDescriptor pluginDescriptor,
                                            @NotNull List<Element> elements,
                                            List<Runnable> listenerCallbacks);

  private void notifyListeners(@NotNull ExtensionEvent event,
                               @NotNull T extensionObject,
                               @NotNull PluginDescriptor pluginDescriptor,
                               ExtensionPointListener<T> @NotNull [] listeners) {
    notifyListeners(event, () -> Collections.singletonList(Pair.create(extensionObject, pluginDescriptor)), listeners);
  }

  private void notifyListeners(@NotNull ExtensionEvent event,
                               @NotNull List<? extends ExtensionComponentAdapter> adapters,
                               ExtensionPointListener<T> @NotNull [] listeners) {
    notifyListeners(event, () -> ContainerUtil.mapNotNull(adapters, adapter ->
      adapter.isInstanceCreated() ? Pair.create(adapter.createInstance(myComponentManager), adapter.getPluginDescriptor()) : null
    ), listeners);
  }

  private void notifyListeners(@NotNull ExtensionEvent event,
                               @NotNull NotNullFactory<List<Pair<T, PluginDescriptor>>> extensions,
                               ExtensionPointListener<T> @NotNull [] listeners) {
    List<Pair<T, PluginDescriptor>> extensionsList = null;
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
        // initialize list of extensions lazily to avoid unnecessary calculations
        if (extensionsList == null) {
          extensionsList = extensions.create();
        }
        for (Pair<? extends T, PluginDescriptor> extension : extensionsList) {
          try {
            switch (event) {
              case REMOVED:
                listener.extensionRemoved(extension.first, extension.second);
                break;
              case ADDED:
                listener.extensionAdded(extension.first, extension.second);
                break;
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
  public synchronized void addExtensionPointListener(@NotNull ExtensionPointListener<T> listener,
                                                     boolean invokeForLoadedExtensions,
                                                     @Nullable Disposable parentDisposable) {
    if (invokeForLoadedExtensions) {
      getExtensionList();
    }

    boolean isAdded = addListener(listener);
    if (isAdded && invokeForLoadedExtensions) {
      //noinspection unchecked
      notifyListeners(ExtensionEvent.ADDED, myAdapters, new ExtensionPointListener[]{listener});
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
    if (ArrayUtil.indexOf(myListeners, listener) != -1) return false;
    if (listener instanceof ExtensionPointPriorityListener) {
      myListeners = ArrayUtil.prepend(listener, myListeners, listenerArrayFactory());
    }
    else {
      myListeners = ArrayUtil.append(myListeners, listener, listenerArrayFactory());
    }
    return true;
  }

  private void removeListener(@NotNull ExtensionPointListener<T> listener) {
    myListeners = ArrayUtil.remove(myListeners, listener, listenerArrayFactory());
  }

  @Override
  public synchronized void addExtensionPointListener(@NotNull ExtensionPointListener<T> listener) {
    addExtensionPointListener(listener, true, null);
  }

  @Override
  public void addExtensionPointListener(@NotNull ExtensionPointChangeListener listener,
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
  public synchronized void removeExtensionPointListener(@NotNull ExtensionPointListener<T> listener) {
    removeListener(listener);
  }

  @Override
  public synchronized void reset() {
    List<ExtensionComponentAdapter> adapters = myAdapters;
    myAdapters = Collections.emptyList();
    if (!adapters.isEmpty()) {
      notifyListeners(ExtensionEvent.REMOVED, adapters, myListeners);
    }
    clearCache();
  }

  @NotNull
  private static <T> T castComponentInstance(@NotNull ExtensionComponentAdapter adapter) {
    //noinspection unchecked
    return ((ObjectComponentAdapter<T>)adapter).myComponentInstance;
  }

  @NotNull
  public Class<T> getExtensionClass() {
    // racy single-check: we don't care whether the access to 'myExtensionClass' is thread-safe
    // but initial store in a local variable is crucial to prevent instruction reordering
    // see Item 71 in Effective Java or http://jeremymanson.blogspot.com/2008/12/benign-data-races-in-java.html
    Class<T> extensionClass = myExtensionClass;
    if (extensionClass == null) {
      try {
        ClassLoader pluginClassLoader = myDescriptor.getPluginClassLoader();
        @SuppressWarnings("unchecked") Class<T> extClass =
          (Class<T>)(pluginClassLoader == null ? Class.forName(myClassName) : Class.forName(myClassName, true, pluginClassLoader));
        myExtensionClass = extensionClass = extClass;
      }
      catch (ClassNotFoundException e) {
        throw myComponentManager.createError(e, myDescriptor.getPluginId());
      }
    }
    return extensionClass;
  }

  public void clearExtensionClass() {
    myExtensionClass = null;
  }

  @Override
  public String toString() {
    return getName();
  }

  // private, internal only for tests
  synchronized void addExtensionAdapter(@NotNull ExtensionComponentAdapter adapter) {
    List<ExtensionComponentAdapter> list = new ArrayList<>(myAdapters.size() + 1);
    list.addAll(myAdapters);
    list.add(adapter);
    myAdapters = list;
    clearCache();
  }

  private void clearCache() {
    myExtensionsCache = null;
    myExtensionsCacheAsArray = null;
    myAdaptersIsSorted = false;

    // asserted here because clearCache is called on any write action
    assertNotReadOnlyMode();
  }

  private void assertNotReadOnlyMode() {
    if (isInReadOnlyMode()) {
      throw new IllegalStateException(this + " in a read-only mode and cannot be modified");
    }
  }

  @NotNull
  protected abstract ExtensionComponentAdapter createAdapterAndRegisterInPicoContainerIfNeeded(@NotNull Element extensionElement,
                                                                                               @NotNull PluginDescriptor pluginDescriptor,
                                                                                               @NotNull ComponentManager componentManager);

  synchronized void createAndRegisterAdapter(@NotNull Element extensionElement,
                                             @NotNull PluginDescriptor pluginDescriptor,
                                             @NotNull ComponentManager componentManager) {
    addExtensionAdapter(createAdapterAndRegisterInPicoContainerIfNeeded(extensionElement, pluginDescriptor, componentManager));
  }

  /**
   * {@link #clearCache} is not called.
   *
   * myAdapters is modified directly without copying - method must be called only during start-up.
   */
  final synchronized void registerExtensions(@NotNull List<? extends Element> extensionElements,
                                             @NotNull IdeaPluginDescriptor pluginDescriptor,
                                             @NotNull ComponentManager componentManager,
                                             @Nullable List<Runnable> listenerCallbacks) {
    if (myComponentManager != componentManager) {
      LOG.error("The same point on different levels (pointName=" + getName() + ")");
    }

    List<ExtensionComponentAdapter> adapters = myAdapters;
    if (adapters == Collections.<ExtensionComponentAdapter>emptyList()) {
      adapters = new ArrayList<>(extensionElements.size());
      myAdapters = adapters;
      myAdaptersIsSorted = false;
    }
    else {
      ((ArrayList<ExtensionComponentAdapter>)adapters).ensureCapacity(adapters.size() + extensionElements.size());
    }

    int oldSize = adapters.size();
    for (Element extensionElement : extensionElements) {
      adapters.add(createAdapterAndRegisterInPicoContainerIfNeeded(extensionElement, pluginDescriptor, componentManager));
    }
    int newSize = myAdapters.size();

    if (listenerCallbacks != null) {
      clearCache();

      listenerCallbacks.add(() -> {
        notifyListeners(ExtensionEvent.ADDED, () -> {
          return ContainerUtil.map(myAdapters.subList(oldSize, newSize),
                                   adapter -> Pair.create(processAdapter(adapter), pluginDescriptor));
        }, myListeners);
      });
    }
  }

  @TestOnly
  final synchronized void notifyAreaReplaced(@NotNull ExtensionsArea oldArea) {
    for (final ExtensionPointListener<T> listener : myListeners) {
      if (listener instanceof ExtensionPointAndAreaListener) {
        ((ExtensionPointAndAreaListener<?>)listener).areaReplaced(oldArea);
      }
    }
  }

  @TestOnly
  @Nullable
  public final PluginDescriptor getPluginDescriptor(@NotNull T extension) {
    for (ExtensionComponentAdapter adapter : getThreadSafeAdapterList(false)) {
      if (processAdapter(adapter) == extension) {
        return adapter.getPluginDescriptor();
      }
    }
    return null;
  }

  @Nullable
  public final <V extends T> V findExtension(@NotNull Class<V> aClass, boolean isRequired, @NotNull ThreeState strictMatch) {
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
          myComponentManager.logError(e, adapter.getPluginDescriptor().getPluginId());
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

  @Nullable
  private T findExtensionByExactClass(@NotNull Class<? extends T> aClass) {
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
    @NotNull
    private final T myComponentInstance;

    private ObjectComponentAdapter(@NotNull T extension,
                                   @NotNull PluginDescriptor pluginDescriptor,
                                   @NotNull LoadingOrder loadingOrder) {
      super(extension.getClass().getName(), pluginDescriptor, null, loadingOrder);

      myComponentInstance = extension;
    }

    @Override
    boolean isInstanceCreated() {
      return true;
    }

    @NotNull
    @Override
    public <I> I createInstance(@Nullable ComponentManager componentManager) {
      //noinspection unchecked
      return (I)myComponentInstance;
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

  private enum ExtensionEvent {
    ADDED, REMOVED
  }
}
