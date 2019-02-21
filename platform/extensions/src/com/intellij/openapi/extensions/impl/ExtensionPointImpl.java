// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OpenTHashSet;
import com.intellij.util.containers.StringInterner;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.PicoContainer;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * @author AKireyev
 */
@SuppressWarnings({"SynchronizeOnThis", "NonPrivateFieldAccessedInSynchronizedContext"})
public abstract class ExtensionPointImpl<T> implements ExtensionPoint<T> {
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

  protected final ExtensionsAreaImpl myOwner;
  private final PluginDescriptor myDescriptor;

  // guarded by this
  @NotNull
  protected List<ExtensionComponentAdapter> myAdapters = Collections.emptyList();

  // guarded by this
  @NotNull
  private List<ExtensionComponentAdapter> myLoadedAdapters = Collections.emptyList();

  @SuppressWarnings("unchecked")
  @NotNull
  // guarded by this
  private ExtensionPointListener<T>[] myListeners = ExtensionPointListener.EMPTY_ARRAY;

  @Nullable
  protected Class<T> myExtensionClass;

  private static final StringInterner INTERNER = new StringInterner();

  ExtensionPointImpl(@NotNull String name,
                     @NotNull String className,
                     @NotNull ExtensionsAreaImpl owner,
                     @NotNull PluginDescriptor pluginDescriptor) {
    synchronized (INTERNER) {
      myName = INTERNER.intern(name);
    }
    myClassName = className;
    myOwner = owner;
    myDescriptor = pluginDescriptor;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public AreaInstance getArea() {
    return myOwner.getAreaInstance();
  }

  @NotNull
  @Override
  public String getClassName() {
    return myClassName;
  }

  @Override
  public void registerExtension(@NotNull T extension) {
    registerExtension(extension, LoadingOrder.ANY, null);
  }

  @Override
  public void registerExtension(@NotNull T extension, @NotNull LoadingOrder order) {
    registerExtension(extension, order, null);
  }

  @Override
  public void registerExtension(@NotNull T extension, @NotNull Disposable parentDisposable) {
    registerExtension(extension, LoadingOrder.ANY, parentDisposable);
  }

  @NotNull
  final PluginDescriptor getDescriptor() {
    return myDescriptor;
  }

  // extension will be not part of pico container
  @Override
  public synchronized void registerExtension(@NotNull T extension, @NotNull LoadingOrder order, @Nullable Disposable parentDisposable) {
    checkReadOnlyMode();

    for (ExtensionComponentAdapter adapter : myLoadedAdapters) {
      if (adapter instanceof ObjectComponentAdapter && ((ObjectComponentAdapter)adapter).myComponentInstance == extension) {
        LOG.error("Extension was already added: " + extension);
        return;
      }
    }

    ExtensionComponentAdapter adapter = new ObjectComponentAdapter<>(extension, order);
    if (LoadingOrder.ANY == order) {
      registerExtension(extension, adapter, findInsertionIndexForAnyOrder(), true);
    }
    else {
      registerExtensionAdapter(adapter);
      getExtensionList();
    }

    if (parentDisposable == null) {
      return;
    }

    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        synchronized (ExtensionPointImpl.this) {
          List<ExtensionComponentAdapter> list = myLoadedAdapters;
          int index = ContainerUtil.indexOfIdentity(list, adapter);
          if (index < 0) {
            // if error occurred during processAdapters, adapter maybe moved from myLoadedAdapters to myAdapters
            list = myAdapters;
            index = ContainerUtil.indexOfIdentity(list, adapter);
          }
          if (index < 0) {
            LOG.error("Extension to be removed not found: " + extension);
          }

          list.remove(index);
          clearCache();
          notifyListenersOnRemove(extension, adapter.getPluginDescriptor(), myListeners);
        }
      }
    });
  }

  /**
   * There are valid cases where we need to register a lot of extensions programmatically,
   * e.g. see SqlDialectTemplateRegistrar, so, special method for bulk insertion is introduced.
   */
  public synchronized void registerExtensions(@NotNull List<? extends T> extensions) {
    for (ExtensionComponentAdapter adapter : myLoadedAdapters) {
      if (adapter instanceof ObjectComponentAdapter) {
        Object instance = ((ObjectComponentAdapter)adapter).myComponentInstance;
        if (ContainerUtil.containsIdentity(extensions, instance)) {
          LOG.error("Extension was already added: " + instance);
          return;
        }
      }
    }

    int firstIndex = findInsertionIndexForAnyOrder();
    int index = firstIndex;

    if (myLoadedAdapters == Collections.<ExtensionComponentAdapter>emptyList()) {
      myLoadedAdapters = new ArrayList<>();
    }

    for (T extension : extensions) {
      myLoadedAdapters.add(index++, new ObjectComponentAdapter<>(extension, LoadingOrder.ANY));
    }

    clearCache();

    for (int i = firstIndex; i < index; i++) {
      //noinspection unchecked
      notifyListenersOnAdd(((ObjectComponentAdapter<T>)myLoadedAdapters.get(i)).myComponentInstance, null, myListeners);
    }
  }

  private synchronized int findInsertionIndexForAnyOrder() {
    int index = myLoadedAdapters.size();
    while (index > 0) {
      ExtensionComponentAdapter lastAdapter = myLoadedAdapters.get(index - 1);
      if (lastAdapter.getOrder() == LoadingOrder.LAST) {
        index--;
      }
      else {
        break;
      }
    }
    return index;
  }

  private synchronized void registerExtension(@NotNull T extension, @NotNull ExtensionComponentAdapter adapter, int index, boolean runNotifications) {
    Class<T> extensionClass = getExtensionClass();
    if (!extensionClass.isInstance(extension)) {
      throw new RuntimeException("Extension " + extension.getClass() + " does not implement " + extensionClass + ". It came from " + adapter);
    }

    if (myLoadedAdapters == Collections.<ExtensionComponentAdapter>emptyList()) {
      myLoadedAdapters = new ArrayList<>();
    }

    if (index == -1) {
      myLoadedAdapters.add(adapter);
    }
    else {
      myLoadedAdapters.add(index, adapter);
    }

    if (runNotifications) {
      clearCache();

      // isNotificationSent is used only in registerExtension and not in registration of listener
      // because newly added listener must be notified about existing adapters
      if (!adapter.isNotificationSent()) {
        notifyListenersOnAdd(extension, adapter.getPluginDescriptor(), myListeners);
        adapter.setNotificationSent();
      }
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
      return myAdapters.size() + myLoadedAdapters.size() > 0;
    }
  }

  private boolean processingAdaptersNow; // guarded by this
  @NotNull
  private synchronized T[] processAdapters() {
    if (processingAdaptersNow) {
      throw new IllegalStateException("Recursive processAdapters() detected. You must have called 'getExtensions()' from within your extension constructor - don't. Either pass extension via constructor parameter or call getExtensions() later.");
    }
    checkReadOnlyMode();

    int totalSize = myAdapters.size() + myLoadedAdapters.size();
    Class<T> extensionClass = getExtensionClass();
    T[] result = ArrayUtil.newArray(extensionClass, totalSize);
    if (totalSize == 0) {
      return result;
    }

    // check before to avoid any "restore" work if already cancelled
    CHECK_CANCELED.run();

    processingAdaptersNow = true;
    try {
      ExtensionComponentAdapter[] adapters = myAdapters.toArray(new ExtensionComponentAdapter[totalSize]);
      ArrayUtil.copy(myLoadedAdapters, adapters, myAdapters.size());
      LoadingOrder.sort(adapters);

      Set<ExtensionComponentAdapter> loaded = ContainerUtil.newHashOrEmptySet(myLoadedAdapters);
      OpenTHashSet<T> duplicates = new OpenTHashSet<>(adapters.length);

      myLoadedAdapters = Collections.emptyList();
      myAdapters = Collections.emptyList();

      int extensionIndex = 0;
      for (int i = 0; i < adapters.length; i++) {
        ExtensionComponentAdapter adapter = adapters[i];
        try {
          // do not call CHECK_CANCELED here in loop because it is called by createInstance()
          @SuppressWarnings("unchecked") T extension = (T)adapter.createInstance(myOwner.getPicoContainer());
          if (!duplicates.add(extension)) {
            T duplicate = duplicates.get(extension);
            LOG.error("Duplicate extension found: " + extension + "; " +
                      " Prev extension:  " + duplicate + ";\n" +
                      " Adapter:         " + adapter + ";\n" +
                      " Extension class: " + getExtensionClass() + ";\n" +
                      " result:" + Arrays.asList(result));
          }
          else {
            registerExtension(extension, adapter, -1, !loaded.contains(adapter));
            // registerExtension can throw error for not correct extension, so, add to result only if call successful
            result[extensionIndex++] = extension;
          }
        }
        catch (ExtensionNotApplicableException ignore) {
          if (LOG.isDebugEnabled()) {
            LOG.debug(adapter + " not loaded because it reported that not applicable");
          }
        }
        catch (ProcessCanceledException e) {
          // error occurred - remove loaded adapters
          addBackNotLoadedAdapters(adapters, i);
          throw e;
        }
        catch (Throwable e) {
          try {
            LOG.error(e);
          }
          catch (Throwable testError) {
            addBackNotLoadedAdapters(adapters, i);
            throw testError;
          }
        }
      }

      if (extensionIndex != result.length) {
        result = Arrays.copyOf(result, extensionIndex);
      }
      return result;
    }
    finally {
      processingAdaptersNow = false;
    }
  }

  private synchronized void addBackNotLoadedAdapters(@NotNull ExtensionComponentAdapter[] allAdapters, int failedIndex) {
    List<ExtensionComponentAdapter> adapters = new ArrayList<>(allAdapters.length - failedIndex);
    //noinspection ManualArrayToCollectionCopy
    for (int i = failedIndex; i < allAdapters.length; i++) {
      adapters.add(allAdapters[i]);
    }
    myAdapters = adapters;
  }

  // used in upsource
  // remove extensions for which implementation class not available
  public synchronized void removeUnloadableExtensions() {
    ExtensionComponentAdapter[] adapters = myAdapters.toArray(ExtensionComponentAdapter.EMPTY_ARRAY);
    for (ExtensionComponentAdapter adapter : adapters) {
      try {
        adapter.getImplementationClass();
      }
      catch (Throwable e) {
        unregisterExtensionAdapter(adapter);
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
   * For tests this method is more preferable than {@link #registerExtension} because makes registration more isolated and strict
   * (no one can modify extension point until `parentDisposable` is not disposed).
   *
   * Please use {@link com.intellij.testFramework.PlatformTestUtil#maskExtensions(ExtensionPointName, List, Disposable)} instead of direct usage.
   */
  @SuppressWarnings("JavadocReference")
  @TestOnly
  public synchronized void maskAll(@NotNull List<T> list, @NotNull Disposable parentDisposable) {
    if (POINTS_IN_READONLY_MODE == null) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      POINTS_IN_READONLY_MODE = ContainerUtil.newIdentityTroveSet();
    }
    else {
      checkReadOnlyMode();
    }

    List<T> oldList = myExtensionsCache;
    T[] oldArray = myExtensionsCacheAsArray;
    // any read access will use supplied list, any write access can lead to unpredictable results - asserted in clearCache
    myExtensionsCache = list;
    myExtensionsCacheAsArray = list.toArray(ArrayUtil.newArray(getExtensionClass(), 0));
    POINTS_IN_READONLY_MODE.add(this);

    if (oldList != null) {
      for (T extension : oldList) {
        notifyListenersOnRemove(extension, null, myListeners);
      }
    }
    for (T extension : list) {
      notifyListenersOnAdd(extension, null, myListeners);
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
  public synchronized void unregisterExtensions(@NotNull Predicate<T> filter) {
    List<T> extensions = getExtensionList();
    List<ExtensionComponentAdapter> loadedAdapters = myLoadedAdapters;
    for (int i = loadedAdapters.size() - 1; i >= 0; i--) {
      T extension = extensions.get(i);
      if (filter.test(extension)) {
        continue;
      }

      ExtensionComponentAdapter adapter = loadedAdapters.remove(i);
      notifyListenersOnRemove(extension, adapter.getPluginDescriptor(), myListeners);
    }

    clearCache();
  }

  @Override
  public synchronized void unregisterExtension(@NotNull T extension) {
    T[] extensions = myExtensionsCacheAsArray;
    List<ExtensionComponentAdapter> loadedAdapters = myLoadedAdapters;
    for (int i = 0; i < loadedAdapters.size(); i++) {
      ExtensionComponentAdapter adapter = loadedAdapters.get(i);
      if (adapter instanceof ObjectComponentAdapter) {
        if (((ObjectComponentAdapter)adapter).myComponentInstance != extension) {
          continue;
        }
      }
      else if (extensions == null || extensions[i] != extension) {
        continue;
      }

      loadedAdapters.remove(i);
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
  public synchronized boolean unregisterExtensions(@NotNull BiPredicate<String, ExtensionComponentAdapter> extensionClassFilter, boolean stopAfterFirstMatch) {
    // here both myAdapters and myLoadedAdapters are checked because goal if this method - unregister extension without instantiation
    boolean found = false;
    for (ExtensionComponentAdapter adapter : ContainerUtil.concat(myAdapters, myLoadedAdapters)) {
      if (extensionClassFilter.test(adapter.getAssignableToClassName(), adapter)) {
        continue;
      }

      unregisterExtensionAdapter(adapter);
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

  // true if added
  private synchronized boolean addListener(@NotNull ExtensionPointListener<T> listener) {
    if (ArrayUtil.indexOf(myListeners, listener) != -1) return false;
    //noinspection unchecked
    myListeners = ArrayUtil.append(myListeners, listener, n-> n == 0 ? ExtensionPointListener.EMPTY_ARRAY : new ExtensionPointListener[n]);
    return true;
  }

  @SuppressWarnings("UnusedReturnValue")
  private synchronized boolean removeListener(@NotNull ExtensionPointListener<T> listener) {
    if (ArrayUtil.indexOf(myListeners, listener) == -1) return false;
    //noinspection unchecked
    myListeners = ArrayUtil.remove(myListeners, listener, n-> n == 0 ? ExtensionPointListener.EMPTY_ARRAY : new ExtensionPointListener[n]);
    return true;
  }

  @Override
  public synchronized void addExtensionPointListener(@NotNull ExtensionPointListener<T> listener) {
    // old contract - "on add point listener, instantiate all point extensions and call listeners for all loaded"
    getExtensionList();

    if (addListener(listener)) {
      notifyListenersAboutLoadedExtensions(myLoadedAdapters, listener, false);
    }
  }

  @Override
  public void removeExtensionPointListener(@NotNull ExtensionPointListener<T> listener) {
    removeListener(listener);
  }

  @Override
  public synchronized void reset() {
    List<ExtensionComponentAdapter> loadedAdapters = myLoadedAdapters;
    myLoadedAdapters = Collections.emptyList();
    myAdapters = Collections.emptyList();

    notifyListenersAboutLoadedExtensions(loadedAdapters, null, true);

    clearCache();
  }

  private synchronized void notifyListenersAboutLoadedExtensions(@NotNull List<ExtensionComponentAdapter> loadedAdapters,
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
        //noinspection unchecked
        extension = ((ObjectComponentAdapter<T>)adapter).myComponentInstance;
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
  synchronized void registerExtensionAdapter(@NotNull ExtensionComponentAdapter adapter) {
    if (myAdapters == Collections.<ExtensionComponentAdapter>emptyList()) {
      myAdapters = new ArrayList<>();
    }
    myAdapters.add(adapter);
    clearCache();
  }

  private synchronized void clearCache() {
    myExtensionsCache = null;
    myExtensionsCacheAsArray = null;

    // asserted here because clearCache is called on any write action
    checkReadOnlyMode();
  }

  private void checkReadOnlyMode() {
    if (isInReadOnlyMode()) {
      throw new IllegalStateException(this + " in a read-only mode and cannot be modified");
    }
  }

  private synchronized void unregisterExtensionAdapter(@NotNull ExtensionComponentAdapter adapter) {
    try {
      // myAdapters will be not empty if adapters were not yet processed (not loaded)
      if (!myAdapters.isEmpty() && myAdapters.remove(adapter)) {
        return;
      }

      int index = ContainerUtil.indexOfIdentity(myLoadedAdapters, adapter);
      if (index == -1) {
        // opposite to remove by extension instance, error "cannot find adapter" is not thrown - it was old behavior and it is preserved,
        // maybe later we will reconsider this aspect.
        return;
      }

      if (adapter instanceof ComponentAdapter) {
        myOwner.getPicoContainer().unregisterComponent(((ComponentAdapter)adapter).getComponentKey());
      }

      myLoadedAdapters.remove(index);

      T extensionInstance;
      // it is not optimization - ObjectComponentAdapter must be checked explicitly because there is a chance that adapters were not yet processed,
      // but adapter was registered explicitly (and so, already initialized and we must call `extensionRemoved` for this instance)
      if (adapter instanceof ObjectComponentAdapter) {
        //noinspection unchecked
        extensionInstance = (T)((ObjectComponentAdapter)adapter).myComponentInstance;
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
    finally {
      clearCache();
    }
  }

  @NotNull
  protected abstract ExtensionComponentAdapter createAdapter(@NotNull Element extensionElement, @NotNull PluginDescriptor pluginDescriptor);

  @NotNull
  ExtensionComponentAdapter createAndRegisterAdapter(@NotNull Element extensionElement, @NotNull PluginDescriptor pluginDescriptor) {
    ExtensionComponentAdapter adapter = createAdapter(extensionElement, pluginDescriptor);
    registerExtensionAdapter(adapter);
    return adapter;
  }

  @NotNull
  protected static ExtensionComponentAdapter doCreateAdapter(@NotNull String implementationClassName,
                                                             @NotNull Element extensionElement,
                                                             boolean isNeedToDeserialize,
                                                             @NotNull PluginDescriptor pluginDescriptor,
                                                             boolean isConstructorInjectionSupported) {
    String orderId = extensionElement.getAttributeValue("id");
    LoadingOrder order = LoadingOrder.readOrder(extensionElement.getAttributeValue("order"));
    Element effectiveElement = isNeedToDeserialize ? extensionElement : null;
    if (isConstructorInjectionSupported) {
      return new XmlExtensionAdapter.ConstructorInjectionAdapter(implementationClassName, pluginDescriptor, orderId, order, effectiveElement);
    }
    else {
      return new XmlExtensionAdapter(implementationClassName, pluginDescriptor, orderId, order, effectiveElement);
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

  private static final class ObjectComponentAdapter<T> extends ExtensionComponentAdapter {
    private final T myComponentInstance;

    private ObjectComponentAdapter(@NotNull T extension, @NotNull LoadingOrder loadingOrder) {
      super(extension.getClass().getName(), null, null, loadingOrder);

      myComponentInstance = extension;
    }

    @NotNull
    @Override
    public T createInstance(@Nullable PicoContainer container) {
      return myComponentInstance;
    }
  }

  public synchronized boolean isInReadOnlyMode() {
    return POINTS_IN_READONLY_MODE != null && POINTS_IN_READONLY_MODE.contains(this);
  }

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  static Runnable CHECK_CANCELED = EmptyRunnable.getInstance();

  public static void setCheckCanceledAction(Runnable checkCanceled) {
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

  private static boolean isInsideClassInitializer(StackTraceElement[] trace) {
    //noinspection SpellCheckingInspection
    return Arrays.stream(trace).anyMatch(s -> "<clinit>".equals(s.getMethodName()));
  }
}
