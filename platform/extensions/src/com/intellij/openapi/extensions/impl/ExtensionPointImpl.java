/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.StringInterner;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.MutablePicoContainer;

import java.lang.reflect.Array;
import java.util.*;

/**
 * @author AKireyev
 */
public class ExtensionPointImpl<T> implements ExtensionPoint<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.extensions.impl.ExtensionPointImpl");

  private final LogProvider myLogger;

  private final AreaInstance myArea;
  private final String myName;
  private final String myClassName;
  private final Kind myKind;

  private final List<T> myExtensions = new ArrayList<T>();
  private volatile T[] myExtensionsCache;

  private final ExtensionsAreaImpl myOwner;
  private final PluginDescriptor myDescriptor;

  private final Set<ExtensionComponentAdapter> myExtensionAdapters = new LinkedHashSet<ExtensionComponentAdapter>();
  private final List<ExtensionPointListener<T>> myEPListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<ExtensionComponentAdapter> myLoadedAdapters = new ArrayList<ExtensionComponentAdapter>();

  private Class<T> myExtensionClass;

  private static final StringInterner INTERNER = new StringInterner();

  public ExtensionPointImpl(@NotNull String name,
                            @NotNull String className,
                            @NotNull Kind kind,
                            @NotNull ExtensionsAreaImpl owner,
                            AreaInstance area,
                            @NotNull LogProvider logger,
                            @NotNull PluginDescriptor descriptor) {
    synchronized (INTERNER) {
      myName = INTERNER.intern(name);
    }
    myClassName = className;
    myKind = kind;
    myOwner = owner;
    myArea = area;
    myLogger = logger;
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
  public String getBeanClassName() {
    return myClassName;
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
    assert myExtensions.size() == myLoadedAdapters.size();

    ObjectComponentAdapter adapter = new ObjectComponentAdapter(extension, order);
    assertClass(extension.getClass());

    if (LoadingOrder.ANY == order) {
      int index = myLoadedAdapters.size();
      if (index > 0) {
        ExtensionComponentAdapter lastAdapter = myLoadedAdapters.get(index - 1);
        if (lastAdapter.getOrder() == LoadingOrder.LAST) {
          index--;
        }
      }
      internalRegisterExtension(extension, adapter, index, true);
    }
    else {
      myExtensionAdapters.add(adapter);
      processAdapters();
    }
    clearCache();
  }

  private void internalRegisterExtension(@NotNull T extension, @NotNull ExtensionComponentAdapter adapter, int index, boolean runNotifications) {
    if (myExtensions.contains(extension)) {
      myLogger.error("Extension was already added: " + extension);
      return;
    }
    myExtensions.add(index, extension);
    myLoadedAdapters.add(index, adapter);
    if (runNotifications) {
      if (extension instanceof Extension) {
        try {
          ((Extension)extension).extensionAdded(this);
        }
        catch (Throwable e) {
          myLogger.error(e);
        }
      }

      clearCache();
      notifyListenersOnAdd(extension, adapter.getPluginDescriptor());
    }
  }

  private void notifyListenersOnAdd(@NotNull T extension, final PluginDescriptor pluginDescriptor) {
    for (ExtensionPointListener<T> listener : myEPListeners) {
      try {
        listener.extensionAdded(extension, pluginDescriptor);
      }
      catch (Throwable e) {
        myLogger.error(e);
      }
    }
  }

  @Override
  @NotNull
  public T[] getExtensions() {
    T[] result = myExtensionsCache;
    if (result == null) {
      synchronized (this) {
        result = myExtensionsCache;
        if (result == null) {
          processAdapters();
          Class<T> extensionClass = getExtensionClass();
          @SuppressWarnings("unchecked") T[] a = (T[])Array.newInstance(extensionClass, myExtensions.size());
          result = myExtensions.toArray(a);

          for (int i = result.length - 1; i >= 0; i--) {
            T t = result[i];
            if (i > 0 && result[i] == result[i - 1]) {
              LOG.error("Duplicate extension found: " + t + "; " +
                        " Result:      " + Arrays.toString(result) + ";\n" +
                        " extensions: " + myExtensions + ";\n" +
                        " getExtensionClass(): " + extensionClass + ";\n" +
                        " size:" + myExtensions.size() + ";" + result.length);
            }

            if (!extensionClass.isAssignableFrom(t.getClass())) {
              LOG.error("Extension '" + t.getClass() + "' must be an instance of '" + extensionClass + "'",
                        new ExtensionException(t.getClass()));
              result = ArrayUtil.remove(result, i); // we assume that usually all extensions are OK
            }
          }

          myExtensionsCache = result;
        }
      }
    }
    return result;
  }

  @Override
  public boolean hasAnyExtensions() {
    final T[] cache = myExtensionsCache;
    if (cache != null) {
      return cache.length > 0;
    }
    synchronized (this) {
      return myExtensionAdapters.size() + myLoadedAdapters.size() > 0;
    }
  }

  private void processAdapters() {
    int totalSize = myExtensionAdapters.size() + myLoadedAdapters.size();
    if (totalSize != 0) {
      List<ExtensionComponentAdapter> allAdapters = new ArrayList<ExtensionComponentAdapter>(totalSize);
      allAdapters.addAll(myExtensionAdapters);
      allAdapters.addAll(myLoadedAdapters);

      myExtensions.clear();
      ExtensionComponentAdapter[] loadedAdapters = myLoadedAdapters.isEmpty()
                                                   ? ExtensionComponentAdapter.EMPTY_ARRAY
                                                   : myLoadedAdapters.toArray(new ExtensionComponentAdapter[myLoadedAdapters.size()]);
      myLoadedAdapters.clear();
      ExtensionComponentAdapter[] adapters = allAdapters.toArray(new ExtensionComponentAdapter[myExtensionAdapters.size()]);
      LoadingOrder.sort(adapters);
      final List<T> extensions = new ArrayList<T>(adapters.length);
      for (ExtensionComponentAdapter adapter : adapters) {
        @SuppressWarnings("unchecked") T extension = (T)adapter.getExtension();
        assertClass(extension.getClass());
        extensions.add(extension);
      }

      for (int i = 0; i < extensions.size(); i++) {
        T extension = extensions.get(i);
        internalRegisterExtension(extension, adapters[i], myExtensions.size(), ArrayUtilRt.find(loadedAdapters, adapters[i]) == -1);
      }
      myExtensionAdapters.clear();
    }
  }

  @Override
  @Nullable
  public T getExtension() {
    T[] extensions = getExtensions();
    if (extensions.length == 0) return null;

    return extensions[0];
  }

  @Override
  public synchronized boolean hasExtension(@NotNull T extension) {
    processAdapters();
    return myExtensions.contains(extension);
  }

  @Override
  public synchronized void unregisterExtension(@NotNull final T extension) {
    final int index = getExtensionIndex(extension);
    final ExtensionComponentAdapter adapter = myLoadedAdapters.get(index);

    myOwner.getMutablePicoContainer().unregisterComponent(adapter.getComponentKey());
    final MutablePicoContainer[] pluginContainers = myOwner.getPluginContainers();
    for (MutablePicoContainer pluginContainer : pluginContainers) {
      pluginContainer.unregisterComponent(adapter.getComponentKey());
    }
    processAdapters();
    internalUnregisterExtension(extension, null);
  }

  private int getExtensionIndex(@NotNull T extension) {
    int i = myExtensions.indexOf(extension);
    if (i == -1) {
      throw new IllegalArgumentException("Extension to be removed not found: " + extension);
    }
    return i;
  }

  private void internalUnregisterExtension(@NotNull T extension, PluginDescriptor pluginDescriptor) {
    int index = getExtensionIndex(extension);
    myExtensions.remove(index);

    myLoadedAdapters.remove(index);

    clearCache();

    notifyListenersOnRemove(extension, pluginDescriptor);

    if (extension instanceof Extension) {
      Extension o = (Extension)extension;
      try {
        o.extensionRemoved(this);
      }
      catch (Throwable e) {
        myLogger.error(e);
      }
    }
  }

  private void notifyListenersOnRemove(@NotNull T extensionObject, PluginDescriptor pluginDescriptor) {
    for (ExtensionPointListener<T> listener : myEPListeners) {
      try {
        listener.extensionRemoved(extensionObject, pluginDescriptor);
      }
      catch (Throwable e) {
        myLogger.error(e);
      }
    }
  }

  @Override
  public synchronized void addExtensionPointListener(@NotNull final ExtensionPointListener<T> listener,
                                                     @NotNull Disposable parentDisposable) {
    addExtensionPointListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        removeExtensionPointListener(listener);
      }
    });
  }

  @Override
  public synchronized void addExtensionPointListener(@NotNull ExtensionPointListener<T> listener) {
    processAdapters();
    if (myEPListeners.add(listener)) {
      for (ExtensionComponentAdapter componentAdapter : myLoadedAdapters.toArray(new ExtensionComponentAdapter[myLoadedAdapters.size()])) {
        try {
          @SuppressWarnings("unchecked") T extension = (T)componentAdapter.getExtension();
          listener.extensionAdded(extension, componentAdapter.getPluginDescriptor());
        }
        catch (Throwable e) {
          myLogger.error(e);
        }
      }
    }
  }

  @Override
  public synchronized void removeExtensionPointListener(@NotNull ExtensionPointListener<T> listener) {
    for (ExtensionComponentAdapter componentAdapter : myLoadedAdapters.toArray(new ExtensionComponentAdapter[myLoadedAdapters.size()])) {
      try {
        @SuppressWarnings("unchecked") T extension = (T)componentAdapter.getExtension();
        listener.extensionRemoved(extension, componentAdapter.getPluginDescriptor());
      }
      catch (Throwable e) {
        myLogger.error(e);
      }
    }

    boolean success = myEPListeners.remove(listener);
    assert success;
  }

  @Override
  public synchronized void reset() {
    myOwner.removeAllComponents(myExtensionAdapters);
    myExtensionAdapters.clear();
    for (T extension : getExtensions()) {
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
        @SuppressWarnings("unchecked") Class<T> extClass = pluginClassLoader == null
            ? (Class<T>)Class.forName(myClassName) : (Class<T>)Class.forName(myClassName, true, pluginClassLoader);
        myExtensionClass = extensionClass = extClass;
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
    return extensionClass;
  }

  public String toString() {
    return getName();
  }

  synchronized void registerExtensionAdapter(@NotNull ExtensionComponentAdapter adapter) {
    myExtensionAdapters.add(adapter);
    clearCache();
  }

  private void assertClass(@NotNull Class<?> extensionClass) {
    Class<T> expectedClass = getExtensionClass();
    assert expectedClass.isAssignableFrom(extensionClass) : "Expected: " + expectedClass + "; Actual: " + extensionClass;
  }

  private void clearCache() {
    myExtensionsCache = null;
  }

  synchronized boolean unregisterComponentAdapter(@NotNull ExtensionComponentAdapter componentAdapter) {
    try {
      if (myExtensionAdapters.remove(componentAdapter)) {
        return true;
      }
      if (myLoadedAdapters.contains(componentAdapter)) {
        final Object componentKey = componentAdapter.getComponentKey();
        myOwner.getMutablePicoContainer().unregisterComponent(componentKey);
        final MutablePicoContainer[] pluginContainers = myOwner.getPluginContainers();
        for (MutablePicoContainer pluginContainer : pluginContainers) {
          pluginContainer.unregisterComponent(componentKey);
        }

        @SuppressWarnings("unchecked") T extension = (T)componentAdapter.getExtension();
        internalUnregisterExtension(extension, componentAdapter.getPluginDescriptor());
        return true;
      }
      return false;
    }
    finally {
      clearCache();
    }
  }

  @TestOnly
  final synchronized void notifyAreaReplaced(final ExtensionsArea area) {
    for (final ExtensionPointListener<T> listener : myEPListeners) {
      if (listener instanceof ExtensionPointAndAreaListener) {
        ((ExtensionPointAndAreaListener)listener).areaReplaced(area);
      }
    }
  }

  private static class ObjectComponentAdapter extends ExtensionComponentAdapter {
    private final Object myExtension;
    private final LoadingOrder myLoadingOrder;

    private ObjectComponentAdapter(@NotNull Object extension, @NotNull LoadingOrder loadingOrder) {
      super(Object.class.getName(), null, null, null, false);
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

    @Override
    @Nullable
    public String getOrderId() {
      return null;
    }

    @Override
    @NonNls
    public Element getDescribingElement() {
      return new Element("RuntimeExtension: " + myExtension);
    }
  }
}
