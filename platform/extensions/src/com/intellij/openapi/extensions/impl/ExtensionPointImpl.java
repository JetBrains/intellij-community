// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.diagnostic.ParallelActivity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ArrayFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OpenTHashSet;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.intellij.util.pico.DefaultPicoContainer.getActivityLevel;

@SuppressWarnings({"SynchronizeOnThis", "NonPrivateFieldAccessedInSynchronizedContext"})
public abstract class ExtensionPointImpl<T> implements ExtensionPoint<T>, Iterable<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.extensions.impl.ExtensionPointImpl");

  // test-only
  private static Set<ExtensionPointImpl<?>> POINTS_IN_READONLY_MODE;

  private final String myName;
  private final String myClassName;

  private volatile List<T> myExtensionsCache;
  // Since JDK 9 Arrays.ArrayList.toArray() doesn't return T[] array (https://bugs.openjdk.java.net/browse/JDK-6260652),
  // but instead returns Object[], so, we cannot use toArray() anymore.
  // Only array.clone should be used because of performance reasons (https://youtrack.jetbrains.com/issue/IDEA-198172).
  @Nullable
  private volatile T[] myExtensionsCacheAsArray;

  final MutablePicoContainer myPicoContainer;
  private final PluginDescriptor myDescriptor;

  // guarded by this
  @NotNull
  protected List<ExtensionComponentAdapter> myAdapters = Collections.emptyList();

  @SuppressWarnings("unchecked")
  @NotNull
  // guarded by this
  private ExtensionPointListener<T>[] myListeners = ExtensionPointListener.EMPTY_ARRAY;

  @Nullable
  Class<T> myExtensionClass;

  ExtensionPointImpl(@NotNull String name,
                     @NotNull String className,
                     @NotNull MutablePicoContainer picoContainer,
                     @NotNull PluginDescriptor pluginDescriptor) {
    myName = name;
    myClassName = className;
    myPicoContainer = picoContainer;
    myDescriptor = pluginDescriptor;
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
  public void registerExtension(@NotNull T extension) {
    doRegisterExtension(extension, LoadingOrder.ANY, null);
  }

  @Override
  public void registerExtension(@NotNull T extension, @NotNull LoadingOrder order) {
    doRegisterExtension(extension, order, null);
  }

  @Override
  public void registerExtension(@NotNull T extension, @NotNull Disposable parentDisposable) {
    doRegisterExtension(extension, LoadingOrder.ANY, parentDisposable);
  }

  @NotNull
  final PluginDescriptor getDescriptor() {
    return myDescriptor;
  }

  @Override
  public synchronized void registerExtension(@NotNull T extension, @NotNull LoadingOrder order, @NotNull Disposable parentDisposable) {
    doRegisterExtension(extension, order, parentDisposable);
  }

  // extension will be not part of pico container
  private synchronized void doRegisterExtension(@NotNull T extension, @NotNull LoadingOrder order, @Nullable Disposable parentDisposable) {
    assertNotReadOnlyMode();
    checkExtensionType(extension, getExtensionClass(), null);

    for (ExtensionComponentAdapter adapter : myAdapters) {
      if (adapter instanceof ObjectComponentAdapter && castComponentInstance(adapter) == extension) {
        LOG.error("Extension was already added: " + extension);
        return;
      }
    }

    ObjectComponentAdapter<T> adapter = new ObjectComponentAdapter<>(extension, order);
    addExtensionAdapter(adapter);
    notifyListenersOnAdd(extension, adapter.getPluginDescriptor(), myListeners);

    if (parentDisposable != null) {
      Disposer.register(parentDisposable, () -> {
        synchronized (this) {
          List<ExtensionComponentAdapter> list = myAdapters;
          int index = ContainerUtil.indexOfIdentity(list, adapter);
          if (index < 0) {
            LOG.error("Extension to be removed not found: " + adapter.myComponentInstance);
          }

          list.remove(index);
          clearCache();
          notifyListenersOnRemove(adapter.myComponentInstance, adapter.getPluginDescriptor(), myListeners);
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

    int firstIndex = findInsertionIndexForAnyOrder();

    if (myAdapters == Collections.<ExtensionComponentAdapter>emptyList()) {
      myAdapters = new ArrayList<>();
    }

    int index = firstIndex;
    for (T extension : extensions) {
      myAdapters.add(index++, new ObjectComponentAdapter<>(extension, LoadingOrder.ANY));
    }

    clearCache();

    for (int i = firstIndex; i < index; i++) {
      notifyListenersOnAdd(castComponentInstance(myAdapters.get(i)), null, myListeners);
    }
  }

  private synchronized int findInsertionIndexForAnyOrder() {
    int index = myAdapters.size();
    while (index > 0) {
      ExtensionComponentAdapter lastAdapter = myAdapters.get(index - 1);
      if (lastAdapter.getOrder() == LoadingOrder.LAST) {
        index--;
      }
      else {
        break;
      }
    }
    return index;
  }

  private synchronized void checkExtensionType(@NotNull T extension, @NotNull Class<T> extensionClass, @Nullable ExtensionComponentAdapter adapter) {
    if (!extensionClass.isInstance(extension)) {
      String message = "Extension " + extension.getClass() + " does not implement " + extensionClass;
      if (adapter != null) {
        message += ". It came from " + adapter;
      }
      throw new RuntimeException(message);
    }
  }

  private void notifyListenersOnAdd(@NotNull T extension, @Nullable PluginDescriptor pluginDescriptor, @NotNull ExtensionPointListener<T>[] listeners) {
    for (ExtensionPointListener<T> listener : listeners) {
      try {
        listener.extensionAdded(extension, pluginDescriptor);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
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
  @NotNull
  public T[] getExtensions() {
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

  @Override
  public void forEachExtensionSafe(Consumer<T> extensionConsumer) {
    for (T t : this) {
      if (t == null) break;
      try {
        extensionConsumer.accept(t);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  /**
   * Do not use it if there is any extension point listener, because in this case behaviour is not predictable -
   * events will be fired during iteration and probably it will be not expected.
   *
   * Use only for interface extension points, not for bean.
   *
   * Due to internal reasons, there is no easy way to implement hasNext in a reliable manner,
   * so, `next` may return `null` (in this case stop iteration).
   */
  @Override
  @ApiStatus.Experimental
  @NotNull
  public final Iterator<T> iterator() {
    List<T> result = myExtensionsCache;
    if (result == null) {
      synchronized (this) {
        result = myExtensionsCache;
        if (result == null) {
          return createIterator();
        }
      }
    }
    return result.iterator();
  }

  public void processWithPluginDescriptor(@NotNull BiConsumer<T, PluginDescriptor> consumer) {
    assertBeforeProcessing();
    CHECK_CANCELED.run();

    if (isInReadOnlyMode()) {
      for (T extension : myExtensionsCache) {
        consumer.accept(extension, myDescriptor /* doesn't matter for tests */);
      }
      return;
    }

    List<ExtensionComponentAdapter> adapters = myAdapters;
    int size = adapters.size();
    if (size == 0) {
      return;
    }

    LoadingOrder.sort(adapters);

    LOG.assertTrue(myListeners.length == 0);

    int currentIndex = 0;
    do {
      ExtensionComponentAdapter adapter = adapters.get(currentIndex++);
      T extension = processAdapter(adapter, null /* don't even pass it */, null, null, null);
      if (extension == null) {
        break;
      }
      consumer.accept(extension, adapter.getPluginDescriptor());
    }
    while (currentIndex < size);
  }

  @NotNull
  private synchronized Iterator<T> createIterator() {
    assertBeforeProcessing();
    CHECK_CANCELED.run();

    List<ExtensionComponentAdapter> adapters = myAdapters;
    int size = adapters.size();
    if (size == 0) {
      return Collections.emptyIterator();
    }

    LoadingOrder.sort(adapters);

    // see method comment about listeners - to ensure that every client of this method doesn't introduce flaky tests/hard to reproduce bugs
    LOG.assertTrue(myListeners.length == 0);

    return new Iterator<T>() {
      private int currentIndex = 0;

      @Override
      public boolean hasNext() {
        return currentIndex < size;
      }

      @Override
      @Nullable
      public T next() {
        do {
          T extension = processAdapter(adapters.get(currentIndex++), null /* don't even pass it */, null, null, null);
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
    final List<T> cache = myExtensionsCache;
    if (cache != null) {
      return !cache.isEmpty();
    }
    synchronized (this) {
      return !myAdapters.isEmpty();
    }
  }

  private boolean processingAdaptersNow; // guarded by this
  @NotNull
  private synchronized T[] processAdapters() {
    assertBeforeProcessing();
    assertNotReadOnlyMode();

    long startTime = StartUpMeasurer.getCurrentTime();

    int totalSize = myAdapters.size();
    Class<T> extensionClass = getExtensionClass();
    T[] result = ArrayUtil.newArray(extensionClass, totalSize);
    if (totalSize == 0) {
      return result;
    }

    // check before to avoid any "restore" work if already cancelled
    CHECK_CANCELED.run();
    processingAdaptersNow = true;
    try {
      List<ExtensionComponentAdapter> adapters = myAdapters;
      LoadingOrder.sort(adapters);

      OpenTHashSet<T> duplicates = this instanceof BeanExtensionPoint ? null : new OpenTHashSet<>(adapters.size());

      ExtensionPointListener<T>[] listeners = myListeners;
      int extensionIndex = 0;
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < adapters.size(); i++) {
        T extension = processAdapter(adapters.get(i), listeners, result, duplicates, extensionClass);
        if (extension != null) {
          result[extensionIndex++] = extension;
        }
      }

      if (extensionIndex != result.length) {
        result = Arrays.copyOf(result, extensionIndex);
      }

      // don't count ProcessCanceledException as valid action to measure (later special category can be introduced if needed)
      ParallelActivity.EXTENSION.record(startTime, extensionClass, getActivityLevel(myPicoContainer));
      return result;
    }
    finally {
      processingAdaptersNow = false;
    }
  }

  @Nullable
  private T processAdapter(@NotNull ExtensionComponentAdapter adapter,
                           @Nullable ExtensionPointListener<T>[] listeners,
                           @Nullable T[] result,
                           @Nullable OpenTHashSet<T> duplicates,
                           @Nullable Class<T> extensionClassForCheck) {
    try {
      boolean isNotifyThatAdded = listeners != null && listeners.length != 0 && !adapter.isInstanceCreated();
      // do not call CHECK_CANCELED here in loop because it is called by createInstance()
      @SuppressWarnings("unchecked")
      T extension = (T)adapter.createInstance(myPicoContainer);
      if (duplicates != null && !duplicates.add(extension)) {
        T duplicate = duplicates.get(extension);
        assert result != null;

        LOG.error("Duplicate extension found:\n" +
                  "                   " + extension + ";\n" +
                  "  prev extension:  " + duplicate + ";\n" +
                  "  adapter:         " + adapter + ";\n" +
                  "  extension class: " + extensionClassForCheck + ";\n" +
                  "  result:          " + Arrays.asList(result) + ";\n" +
                  "  adapters:        " + myAdapters
        );
      }
      else {
        if (extensionClassForCheck != null) {
          checkExtensionType(extension, extensionClassForCheck, adapter);
        }

        if (isNotifyThatAdded) {
          notifyListenersOnAdd(extension, adapter.getPluginDescriptor(), listeners);
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

  private void assertBeforeProcessing() {
    if (processingAdaptersNow) {
      throw new IllegalStateException("Recursive processAdapters() detected. You must have called 'getExtensions()' from within your extension constructor - don't. " +
                                      "Either pass extension via constructor parameter or call getExtensions() later.");
    }
  }

  // used in upsource
  // remove extensions for which implementation class is not available
  public synchronized void removeUnloadableExtensions() {
    List<ExtensionComponentAdapter> adapters = myAdapters;
    for (int i = adapters.size() - 1; i >= 0; i--) {
      ExtensionComponentAdapter adapter = adapters.get(i);
      try {
        adapter.getImplementationClass();
      }
      catch (Throwable e) {
        removeAdapter(adapter, i);
        clearCache();
      }
    }
  }

  @Override
  @Nullable
  public T getExtension() {
    List<T> extensions = getExtensionList();
    return extensions.isEmpty() ? null : extensions.get(0);
  }

  @Override
  public synchronized boolean hasExtension(@NotNull T extension) {
    // method is deprecated and used only by one external plugin, ignore not efficient implementation
    return ContainerUtil.containsIdentity(getExtensionList(), extension);
  }

  /**
   * Put extension point in read-only mode and replace existing extensions by supplied.
   * For tests this method is more preferable than {@link #registerExtension)} because makes registration more isolated and strict
   * (no one can modify extension point until `parentDisposable` is not disposed).
   *
   * Please use {@link com.intellij.testFramework.PlatformTestUtil#maskExtensions(ExtensionPointName, List, Disposable)} instead of direct usage.
   */
  @TestOnly
  public synchronized void maskAll(@NotNull List<T> list, @NotNull Disposable parentDisposable) {
    if (POINTS_IN_READONLY_MODE == null) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      POINTS_IN_READONLY_MODE = ContainerUtil.newIdentityTroveSet();
    }
    else {
      assertNotReadOnlyMode();
    }

    List<T> oldList = myExtensionsCache;
    T[] oldArray = myExtensionsCacheAsArray;
    // any read access will use supplied list, any write access can lead to unpredictable results - asserted in clearCache
    myExtensionsCache = list;
    myExtensionsCacheAsArray = list.toArray(ArrayUtil.newArray(getExtensionClass(), 0));
    POINTS_IN_READONLY_MODE.add(this);

    if (myListeners.length > 0) {
      if (oldList != null) {
        for (T extension : oldList) {
          notifyListenersOnRemove(extension, null, myListeners);
        }
      }
      for (T extension : list) {
        notifyListenersOnAdd(extension, null, myListeners);
      }
    }

    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        synchronized (this) {
          POINTS_IN_READONLY_MODE.remove(ExtensionPointImpl.this);
          myExtensionsCache = oldList;
          myExtensionsCacheAsArray = oldArray;

          for (T extension : list) {
            notifyListenersOnRemove(extension, null, myListeners);
          }
          if (oldList != null) {
            for (T extension : oldList) {
              notifyListenersOnAdd(extension, null, myListeners);
            }
          }
        }
      }
    });
  }

  @Override
  public synchronized void unregisterExtensions(@NotNull Predicate<? super T> filter) {
    List<T> extensions = getExtensionList();
    List<ExtensionComponentAdapter> adapters = myAdapters;
    ExtensionPointListener<T>[] listeners = myListeners;
    List<Pair<T, PluginDescriptor>> removed = listeners.length == 0 ? null : new ArrayList<>();
    for (int i = adapters.size() - 1; i >= 0; i--) {
      T extension = extensions.get(i);
      if (filter.test(extension)) {
        continue;
      }

      ExtensionComponentAdapter adapter = adapters.remove(i);
      if (removed != null) {
        removed.add(new Pair<>(extension, adapter.getPluginDescriptor()));
      }
    }

    clearCache();

    if (removed != null) {
      for (Pair<T, PluginDescriptor> pair : removed) {
        notifyListenersOnRemove(pair.first, pair.second, listeners);
      }
    }
  }

  @Override
  public synchronized void unregisterExtension(@NotNull T extension) {
    T[] extensions = myExtensionsCacheAsArray;
    List<ExtensionComponentAdapter> adapters = myAdapters;
    for (int i = 0; i < adapters.size(); i++) {
      ExtensionComponentAdapter adapter = adapters.get(i);
      if (adapter instanceof ObjectComponentAdapter) {
        if (castComponentInstance(adapter) != extension) {
          continue;
        }
      }
      else if (extensions == null || extensions[i] != extension) {
        continue;
      }

      adapters.remove(i);
      clearCache();
      notifyListenersOnRemove(extension, adapter.getPluginDescriptor(), myListeners);
      return;
    }

    // there is a possible case that particular extension was replaced in particular environment, e.g. Upsource
    // replaces some IntelliJ extensions (important for CoreApplicationEnvironment), so, just log as error instead of throw error
    LOG.warn("Extension to be removed not found: " + extension);
  }

  @Override
  public void unregisterExtension(@NotNull Class<? extends T> extensionClass) {
    String classNameToUnregister = extensionClass.getCanonicalName();
    if (!unregisterExtensions((className, adapter) -> !className.equals(classNameToUnregister), /* stopAfterFirstMatch = */ true)) {
      LOG.warn("Extension to be removed not found: " + extensionClass);
    }
  }

  @Override
  public synchronized boolean unregisterExtensions(@NotNull BiPredicate<? super String, ? super ExtensionComponentAdapter> extensionClassFilter, boolean stopAfterFirstMatch) {
    boolean found = false;
    for (int i = myAdapters.size() - 1; i >= 0; i--) {
      ExtensionComponentAdapter adapter = myAdapters.get(i);
      if (extensionClassFilter.test(adapter.getAssignableToClassName(), adapter)) {
        continue;
      }

      removeAdapter(adapter, i);
      clearCache();
      if (stopAfterFirstMatch) {
        return true;
      }
      else {
        found = true;
      }
    }

    return found;
  }

  private void notifyListenersOnRemove(@NotNull T extensionObject,
                                       @Nullable PluginDescriptor pluginDescriptor,
                                       @NotNull ExtensionPointListener<T>[] listeners) {
    for (ExtensionPointListener<T> listener : listeners) {
      try {
        listener.extensionRemoved(extensionObject, pluginDescriptor);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public synchronized void addExtensionPointListener(@NotNull ExtensionPointListener<T> listener,
                                                     boolean invokeForLoadedExtensions,
                                                     @Nullable Disposable parentDisposable) {
    if (invokeForLoadedExtensions) {
      addExtensionPointListener(listener);
    }
    else {
      addListener(listener);
    }

    if (parentDisposable != null) {
      Disposer.register(parentDisposable, () -> removeExtensionPointListener(listener));
    }
  }

  private static final ArrayFactory<ExtensionPointListener> LISTENER_ARRAY_FACTORY =
    n -> n == 0 ? ExtensionPointListener.EMPTY_ARRAY : new ExtensionPointListener[n];
  private static <T> ArrayFactory<ExtensionPointListener<T>> listenerArrayFactory() {
    //noinspection unchecked
    return (ArrayFactory)LISTENER_ARRAY_FACTORY;
  }

  // true if added
  private synchronized boolean addListener(@NotNull ExtensionPointListener<T> listener) {
    if (ArrayUtil.indexOf(myListeners, listener) != -1) return false;
    myListeners = ArrayUtil.append(myListeners, listener, listenerArrayFactory());
    return true;
  }

  private synchronized void removeListener(@NotNull ExtensionPointListener<T> listener) {
    myListeners = ArrayUtil.remove(myListeners, listener, listenerArrayFactory());
  }

  @Override
  public synchronized void addExtensionPointListener(@NotNull ExtensionPointListener<T> listener) {
    // old contract - "on add point listener, instantiate all point extensions and call listeners for all loaded"
    getExtensionList();

    if (addListener(listener)) {
      notifyListenersAboutLoadedExtensions(myAdapters, listener, false);
    }
  }

  @Override
  public void removeExtensionPointListener(@NotNull ExtensionPointListener<T> listener) {
    removeListener(listener);
  }

  @Override
  public synchronized void reset() {
    List<ExtensionComponentAdapter> adapters = myAdapters;
    myAdapters = Collections.emptyList();
    notifyListenersAboutLoadedExtensions(adapters, null, true);
    clearCache();
  }

  private static <T> T castComponentInstance(@NotNull ExtensionComponentAdapter adapter) {
    //noinspection unchecked
    return ((ObjectComponentAdapter<T>)adapter).myComponentInstance;
  }

  private synchronized void notifyListenersAboutLoadedExtensions(@NotNull List<? extends ExtensionComponentAdapter> loadedAdapters,
                                                                 @Nullable ExtensionPointListener<T> onlyListener,
                                                                 boolean isRemoved) {
    ExtensionPointListener<T>[] listeners = myListeners;
    if (listeners.length == 0) {
      return;
    }

    T[] extensions = myExtensionsCacheAsArray;
    for (int i = 0, size = loadedAdapters.size(); i < size; i++) {
      ExtensionComponentAdapter adapter = loadedAdapters.get(i);
      T extension;
      if (adapter instanceof ObjectComponentAdapter) {
        extension = castComponentInstance(adapter);
      }
      else if (extensions == null) {
        continue;
      }
      else {
        extension = extensions[i];
      }

      if (isRemoved) {
        if (onlyListener == null) {
          notifyListenersOnRemove(extension, adapter.getPluginDescriptor(), listeners);
        }
        else {
          onlyListener.extensionRemoved(extension, adapter.getPluginDescriptor());
        }
      }
      else {
        if (onlyListener == null) {
          notifyListenersOnAdd(extension, adapter.getPluginDescriptor(), listeners);
        }
        else {
          onlyListener.extensionAdded(extension, adapter.getPluginDescriptor());
        }
      }
    }
  }

  @NotNull
  @Override
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
        throw new RuntimeException(e);
      }
    }
    return extensionClass;
  }

  @Override
  public String toString() {
    return getName();
  }

  // private, internal only for tests
  synchronized void addExtensionAdapter(@NotNull ExtensionComponentAdapter adapter) {
    if (myAdapters == Collections.<ExtensionComponentAdapter>emptyList()) {
      myAdapters = new ArrayList<>();
    }
    myAdapters.add(adapter);
    clearCache();
  }

  synchronized void clearCache() {
    myExtensionsCache = null;
    myExtensionsCacheAsArray = null;

    // asserted here because clearCache is called on any write action
    assertNotReadOnlyMode();
  }

  private void assertNotReadOnlyMode() {
    if (isInReadOnlyMode()) {
      throw new IllegalStateException(this + " in a read-only mode and cannot be modified");
    }
  }

  private synchronized void removeAdapter(@NotNull ExtensionComponentAdapter adapter, int index) {
    if (adapter instanceof ComponentAdapter) {
      myPicoContainer.unregisterComponent(((ComponentAdapter)adapter).getComponentKey());
    }
    myAdapters.remove(index);

    T extensionInstance;
    // it is not optimization - ObjectComponentAdapter must be checked explicitly because there is a chance that adapters were not yet processed,
    // but adapter was registered explicitly (and so, already initialized and we must call `extensionRemoved` for this instance)
    if (adapter instanceof ExtensionPointImpl.ObjectComponentAdapter) {
      extensionInstance = castComponentInstance(adapter);
    }
    else {
      T[] array = myExtensionsCacheAsArray;
      if (array == null) {
        return;
      }
      else {
        extensionInstance = array[index];
      }
    }
    notifyListenersOnRemove(extensionInstance, adapter.getPluginDescriptor(), myListeners);
  }

  @NotNull
  protected abstract ExtensionComponentAdapter createAdapterAndRegisterInPicoContainerIfNeeded(@NotNull Element extensionElement,
                                                                                               @NotNull PluginDescriptor pluginDescriptor,
                                                                                               @NotNull MutablePicoContainer picoContainer);

  void createAndRegisterAdapter(@NotNull Element extensionElement, @NotNull PluginDescriptor pluginDescriptor, @NotNull MutablePicoContainer picoContainer) {
    addExtensionAdapter(createAdapterAndRegisterInPicoContainerIfNeeded(extensionElement, pluginDescriptor, picoContainer));
  }

  /**
   * {@link #clearCache} is not called, use {@link ExtensionsAreaImpl#extensionsRegistered(ExtensionPointImpl[])} if needed.
   */
  public final synchronized void createAndRegisterAdapters(@NotNull Collection<? extends Element> extensionElements,
                                                           @NotNull PluginDescriptor pluginDescriptor,
                                                           @NotNull MutablePicoContainer picoContainer) {
    if (extensionElements.isEmpty()) {
      return;
    }

    List<ExtensionComponentAdapter> adapters = myAdapters;
    if (adapters == Collections.<ExtensionComponentAdapter>emptyList()) {
      adapters = new ArrayList<>(extensionElements.size());
      myAdapters = adapters;
    }
    else {
      ((ArrayList<ExtensionComponentAdapter>)adapters).ensureCapacity(adapters.size() + extensionElements.size());
    }

    for (Element extensionElement : extensionElements) {
      adapters.add(createAdapterAndRegisterInPicoContainerIfNeeded(extensionElement, pluginDescriptor, picoContainer));
    }
  }

  @TestOnly
  final synchronized void notifyAreaReplaced(@NotNull ExtensionsArea oldArea) {
    for (final ExtensionPointListener<T> listener : myListeners) {
      if (listener instanceof ExtensionPointAndAreaListener) {
        ((ExtensionPointAndAreaListener)listener).areaReplaced(oldArea);
      }
    }
  }

  @Nullable
  public synchronized T findExtension(@NotNull Class<T> instanceOf, boolean isRequired) {
    Iterator<T> iterator = myListeners.length == 0 ? iterator() : getExtensionList().iterator();
    while (iterator.hasNext()) {
      T object = iterator.next();
      if (object != null && instanceOf.isInstance(object)) {
        return object;
      }
    }

    if (isRequired) {
      String message = "could not find extension implementation " + instanceOf;
      if (isInReadOnlyMode()) {
        message += " (point in read-only mode)";
      }
      throw new IllegalArgumentException(message);
    }
    return null;
  }

  private static final class ObjectComponentAdapter<T> extends ExtensionComponentAdapter {
    private final T myComponentInstance;

    private ObjectComponentAdapter(@NotNull T extension, @NotNull LoadingOrder loadingOrder) {
      super(extension.getClass().getName(), null, null, loadingOrder);

      myComponentInstance = extension;
    }

    @Override
    boolean isInstanceCreated() {
      return true;
    }

    @NotNull
    @Override
    public T createInstance(@Nullable PicoContainer container) {
      return myComponentInstance;
    }
  }

  protected synchronized boolean isInReadOnlyMode() {
    return POINTS_IN_READONLY_MODE != null && POINTS_IN_READONLY_MODE.contains(this);
  }

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  static Runnable CHECK_CANCELED = EmptyRunnable.getInstance();

  public static void setCheckCanceledAction(@NotNull Runnable checkCanceled) {
    CHECK_CANCELED = () -> {
      try {
        checkCanceled.run();
      }
      catch (ProcessCanceledException e) {
        if (!isInsideClassInitializer(e.getStackTrace())) { // otherwise ExceptionInInitializerError happens and the class is screwed forever
          throw e;
        }
      }
    };
  }

  private static boolean isInsideClassInitializer(@NotNull StackTraceElement[] trace) {
    //noinspection SpellCheckingInspection
    return Arrays.stream(trace).anyMatch(s -> "<clinit>".equals(s.getMethodName()));
  }
}
