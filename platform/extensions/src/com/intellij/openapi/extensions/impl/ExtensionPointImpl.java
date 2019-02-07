// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OpenTHashSet;
import com.intellij.util.containers.StringInterner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Stream;

/**
 * @author AKireyev
 */
@SuppressWarnings("SynchronizeOnThis")
public final class ExtensionPointImpl<T> implements ExtensionPoint<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.extensions.impl.ExtensionPointImpl");

  private final AreaInstance myArea;
  private final String myName;
  private final String myClassName;
  private final Kind myKind;

  private volatile List<T> myExtensionsCache;
  // Since JDK 9 Arrays.ArrayList.toArray() doesn't return T[] array (https://bugs.openjdk.java.net/browse/JDK-6260652),
  // but instead returns Object[], so, we cannot use toArray() anymore.
  // Only array.clone should be used because of performance reasons (https://youtrack.jetbrains.com/issue/IDEA-198172).
  private volatile T[] myExtensionsCacheAsArray;

  private final ExtensionsAreaImpl myOwner;
  private final PluginDescriptor myDescriptor;

  @NotNull
  private Set<ExtensionComponentAdapter> myExtensionAdapters = Collections.emptySet(); // guarded by this
  @SuppressWarnings("unchecked")
  @NotNull
  private ExtensionPointListener<T>[] myEPListeners = ExtensionPointListener.EMPTY_ARRAY; // guarded by this
  @NotNull
  private List<ExtensionComponentAdapter> myLoadedAdapters = Collections.emptyList(); // guarded by this

  private Class<T> myExtensionClass;

  private static final StringInterner INTERNER = new StringInterner();

  ExtensionPointImpl(@NotNull String name,
                     @NotNull String className,
                     @NotNull Kind kind,
                     @NotNull ExtensionsAreaImpl owner,
                     AreaInstance area,
                     @NotNull PluginDescriptor descriptor) {
    synchronized (INTERNER) {
      myName = INTERNER.intern(name);
    }
    myClassName = className;
    myKind = kind;
    myOwner = owner;
    myArea = area;
    myDescriptor = descriptor;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public AreaInstance getArea() {
    return myArea;
  }

  @NotNull
  @Override
  public String getClassName() {
    return myClassName;
  }

  @NotNull
  @Override
  public Kind getKind() {
    return myKind;
  }

  @Override
  public void registerExtension(@NotNull T extension) {
    registerExtension(extension, LoadingOrder.ANY);
  }

  @NotNull
  public PluginDescriptor getDescriptor() {
    return myDescriptor;
  }

  @Override
  public synchronized void registerExtension(@NotNull T extension, @NotNull LoadingOrder order) {
    ExtensionComponentAdapter adapter = new ObjectComponentAdapter(extension, order);

    if (LoadingOrder.ANY == order) {
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
      if (getExtensionIndex(extension) != -1) {
        LOG.error("Extension was already added: " + extension);
        return;
      }

      registerExtension(extension, adapter, index, true);
    }
    else {
      registerExtensionAdapter(adapter);
      processAdapters();
    }
  }

  private void registerExtension(@NotNull T extension, @NotNull ExtensionComponentAdapter adapter, int index, boolean runNotifications) {
    Class<T> extensionClass = getExtensionClass();
    if (!extensionClass.isInstance(extension)) {
      LOG.error("Extension " + extension.getClass() + " does not implement " + extensionClass);
      return;
    }
    if (myLoadedAdapters == Collections.<ExtensionComponentAdapter>emptyList()) myLoadedAdapters = new ArrayList<>();
    myLoadedAdapters.add(index, adapter);

    if (runNotifications) {
      clearCache();

      if (!adapter.isNotificationSent()) {
        if (extension instanceof Extension) {
          try {
            ((Extension)extension).extensionAdded(this);
          }
          catch (Throwable e) {
            LOG.error(e);
          }
        }

        notifyListenersOnAdd(extension, adapter.getPluginDescriptor());
        adapter.setNotificationSent();
      }
    }
  }

  private void notifyListenersOnAdd(@NotNull T extension, final PluginDescriptor pluginDescriptor) {
    for (ExtensionPointListener<T> listener : myEPListeners) {
      try {
        listener.extensionAdded(extension, pluginDescriptor);
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
      return myExtensionAdapters.size() + myLoadedAdapters.size() > 0;
    }
  }

  private boolean processingAdaptersNow; // guarded by this
  @NotNull
  private T[] processAdapters() {
    if (processingAdaptersNow) {
      throw new IllegalStateException("Recursive processAdapters() detected. You must have called 'getExtensions()' from within your extension constructor - don't. Either pass extension via constructor parameter or call getExtensions() later.");
    }
    int totalSize = myExtensionAdapters.size() + myLoadedAdapters.size();
    Class<T> extensionClass = getExtensionClass();
    @SuppressWarnings("unchecked")
    T[] result = (T[])Array.newInstance(extensionClass, totalSize);
    if (totalSize == 0) {
      return result;
    }

    processingAdaptersNow = true;
    try {
      ExtensionComponentAdapter[] adapters = new ExtensionComponentAdapter[totalSize];
      myExtensionAdapters.toArray(adapters);
      ArrayUtil.copy(myLoadedAdapters, adapters, myExtensionAdapters.size());
      LoadingOrder.sort(adapters);
      myExtensionAdapters = new LinkedHashSet<>(adapters.length);
      ContainerUtil.addAll(myExtensionAdapters, adapters);

      Set<ExtensionComponentAdapter> loaded = ContainerUtil.newHashOrEmptySet(myLoadedAdapters);
      OpenTHashSet<T> duplicates = new OpenTHashSet<>(adapters.length);

      myLoadedAdapters = Collections.emptyList();
      int extensionIndex = 0;
      for (ExtensionComponentAdapter adapter : adapters) {
        CHECK_CANCELED.run();
        try {
          @SuppressWarnings("unchecked") T extension = (T)adapter.getExtension();
          if (extension == null) {
            LOG.error("null extension in: " + adapter + ";\ngetExtensionClass(): " + getExtensionClass() + ";\n");
          }
          else if (!duplicates.add(extension)) {
            T duplicate = duplicates.get(extension);
            LOG.error("Duplicate extension found: " + extension + "; " +
                      " Prev extension: " + duplicate + ";\n" +
                      " Adapter:        " + adapter + ";\n" +
                      " getExtensionClass(): " + getExtensionClass() + ";\n" +
                      " result:" + Arrays.asList(result));
          }
          else if (!extensionClass.isInstance(extension)) {
            LOG.error("Extension " + extension.getClass() + " does not implement " + extensionClass + ". It came from " + adapter);
          }
          else {
            result[extensionIndex++] = extension;
            registerExtension(extension, adapter, myLoadedAdapters.size(), !loaded.contains(adapter));
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
        catch (Exception e) {
          LOG.error(e);
        }
        myExtensionAdapters.remove(adapter);
      }
      myExtensionAdapters = Collections.emptySet();

      if (extensionIndex != result.length) {
        result = Arrays.copyOf(result, extensionIndex);
      }
      return result;
    }
    finally {
      processingAdaptersNow = false;
    }
  }

  public synchronized void removeUnloadableExtensions() {
    ExtensionComponentAdapter[] adapters = myExtensionAdapters.toArray(ExtensionComponentAdapter.EMPTY_ARRAY);
    for (ExtensionComponentAdapter adapter : adapters) {
      try {
        adapter.getComponentImplementation();
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
    T[] extensions = processAdapters();
    return ArrayUtil.contains(extension, extensions);
  }

  @Override
  public synchronized void unregisterExtension(@NotNull final T extension) {
    final int index = getExtensionIndex(extension);
    if (index == -1) {
      throw new IllegalArgumentException("Extension to be removed not found: " + extension);
    }
    final ExtensionComponentAdapter adapter = myLoadedAdapters.get(index);

    Object key = adapter.getComponentKey();
    myOwner.getPicoContainer().unregisterComponent(key);

    processAdapters();
    unregisterExtension(extension, null);
  }

  @Override
  public synchronized void unregisterExtension(@NotNull Class<? extends T> extensionClass) {
    for (ExtensionComponentAdapter adapter : ContainerUtil.concat(myExtensionAdapters, myLoadedAdapters)) {
      if (adapter.getAssignableToClassName().equals(extensionClass.getCanonicalName())) {
        unregisterExtensionAdapter(adapter);
        return;
      }
    }
    throw new IllegalArgumentException("Extension to be removed not found: " + extensionClass);
  }

  private int getExtensionIndex(@NotNull T extension) {
    for (int i = 0; i < myLoadedAdapters.size(); i++) {
      ExtensionComponentAdapter adapter = myLoadedAdapters.get(i);
      if (Comparing.equal(adapter.getExtension(), extension)) return i;
    }
    return -1;
  }

  private void unregisterExtension(@NotNull T extension, PluginDescriptor pluginDescriptor) {
    int index = getExtensionIndex(extension);
    if (index == -1) {
      throw new IllegalArgumentException("Extension to be removed not found: " + extension);
    }

    myLoadedAdapters.remove(index);
    clearCache();

    notifyListenersOnRemove(extension, pluginDescriptor);

    if (extension instanceof Extension) {
      try {
        ((Extension)extension).extensionRemoved(this);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  private void notifyListenersOnRemove(@NotNull T extensionObject, PluginDescriptor pluginDescriptor) {
    for (ExtensionPointListener<T> listener : myEPListeners) {
      try {
        listener.extensionRemoved(extensionObject, pluginDescriptor);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public void addExtensionPointListener(@NotNull final ExtensionPointListener<T> listener, @NotNull Disposable parentDisposable) {
    addExtensionPointListener(listener, true, parentDisposable);
  }

  public synchronized void addExtensionPointListener(@NotNull final ExtensionPointListener<T> listener,
                                                     final boolean invokeForLoadedExtensions,
                                                     @Nullable Disposable parentDisposable) {
    if (invokeForLoadedExtensions) {
      addExtensionPointListener(listener);
    }
    else {
      addListener(listener);
    }
    if (parentDisposable != null) {
      Disposer.register(parentDisposable, () -> removeExtensionPointListener(listener, invokeForLoadedExtensions));
    }
  }

  // true if added
  private boolean addListener(@NotNull ExtensionPointListener<T> listener) {
    if (ArrayUtil.indexOf(myEPListeners, listener) != -1) return false;
    //noinspection unchecked
    myEPListeners = ArrayUtil.append(myEPListeners, listener, n->n==0?ExtensionPointListener.EMPTY_ARRAY:new ExtensionPointListener[n]);
    return true;
  }
  private boolean removeListener(@NotNull ExtensionPointListener<T> listener) {
    if (ArrayUtil.indexOf(myEPListeners, listener) == -1) return false;
    //noinspection unchecked
    myEPListeners = ArrayUtil.remove(myEPListeners, listener, n->n==0?ExtensionPointListener.EMPTY_ARRAY:new ExtensionPointListener[n]);
    return true;
  }

  @Override
  public synchronized void addExtensionPointListener(@NotNull ExtensionPointListener<T> listener) {
    processAdapters();
    if (addListener(listener)) {
      ExtensionComponentAdapter[] array = myLoadedAdapters.toArray(ExtensionComponentAdapter.EMPTY_ARRAY);
      for (ExtensionComponentAdapter componentAdapter : array) {
        try {
          @SuppressWarnings("unchecked") T extension = (T)componentAdapter.getExtension();
          listener.extensionAdded(extension, componentAdapter.getPluginDescriptor());
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }

  @Override
  public void removeExtensionPointListener(@NotNull ExtensionPointListener<T> listener) {
    removeExtensionPointListener(listener, true);
  }

  private synchronized void removeExtensionPointListener(@NotNull ExtensionPointListener<T> listener, boolean invokeForLoadedExtensions) {
    if (removeListener(listener) && invokeForLoadedExtensions) {
      ExtensionComponentAdapter[] array = myLoadedAdapters.toArray(ExtensionComponentAdapter.EMPTY_ARRAY);
      for (ExtensionComponentAdapter componentAdapter : array) {
        try {
          @SuppressWarnings("unchecked") T extension = (T)componentAdapter.getExtension();
          listener.extensionRemoved(extension, componentAdapter.getPluginDescriptor());
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }

  @Override
  public synchronized void reset() {
    myOwner.removeAllComponents(myExtensionAdapters);
    myExtensionAdapters = Collections.emptySet();
    for (T extension : getExtensionList()) {
      unregisterExtension(extension);
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

  synchronized void registerExtensionAdapter(@NotNull ExtensionComponentAdapter adapter) {
    if (myExtensionAdapters == Collections.<ExtensionComponentAdapter>emptySet()) {
      myExtensionAdapters = new LinkedHashSet<>();
    }
    myExtensionAdapters.add(adapter);
    clearCache();
  }

  private void clearCache() {
    myExtensionsCache = null;
    myExtensionsCacheAsArray = null;
  }

  private void unregisterExtensionAdapter(@NotNull ExtensionComponentAdapter adapter) {
    try {
      if (!myExtensionAdapters.isEmpty() && myExtensionAdapters.remove(adapter)) {
        return;
      }
      if (myLoadedAdapters.contains(adapter)) {
        Object key = adapter.getComponentKey();
        myOwner.getPicoContainer().unregisterComponent(key);

        @SuppressWarnings("unchecked") T extension = (T)adapter.getExtension();
        unregisterExtension(extension, adapter.getPluginDescriptor());
      }
    }
    finally {
      clearCache();
    }
  }

  @TestOnly
  final synchronized void notifyAreaReplaced(@NotNull ExtensionsArea oldArea) {
    for (final ExtensionPointListener<T> listener : myEPListeners) {
      if (listener instanceof ExtensionPointAndAreaListener) {
        ((ExtensionPointAndAreaListener)listener).areaReplaced(oldArea);
      }
    }
  }

  private static class ObjectComponentAdapter extends ExtensionComponentAdapter {
    private final Object myExtension;
    private final LoadingOrder myLoadingOrder;

    private ObjectComponentAdapter(@NotNull Object extension, @NotNull LoadingOrder loadingOrder) {
      super(extension.getClass().getName(), null, null, null, LoadingOrder.ANY, null);
      myExtension = extension;
      myLoadingOrder = loadingOrder;
    }

    @Override
    public Object getExtension() {
      return myExtension;
    }

    @Override
    public LoadingOrder getOrder() {
      return myLoadingOrder;
    }
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
    return Arrays.stream(trace).anyMatch(s -> "<clinit>".equals(s.getMethodName()));
  }
}
