/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
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
  private final List<ExtensionPointListener<T>> myEPListeners = ContainerUtil.createEmptyCOWList();
  private final List<ExtensionComponentAdapter> myLoadedAdapters = new ArrayList<ExtensionComponentAdapter>();

  private Class<T> myExtensionClass;

  public ExtensionPointImpl(String name,
                            String className,
                            Kind kind,
                            ExtensionsAreaImpl owner,
                            AreaInstance area,
                            LogProvider logger,
                            PluginDescriptor descriptor) {
    myName = name;
    myClassName = className;
    myKind = kind;
    myOwner = owner;
    myArea = area;
    myLogger = logger;
    myDescriptor = descriptor;
  }

  public String getName() {
    return myName;
  }

  public AreaInstance getArea() {
    return myArea;
  }

  public String getBeanClassName() {
    return myClassName;
  }

  @Override
  public String getClassName() {
    return myClassName;
  }

  @Override
  public Kind getKind() {
    return myKind;
  }

  public void registerExtension(@NotNull T extension) {
    registerExtension(extension, LoadingOrder.ANY);
  }

  public PluginDescriptor getDescriptor() {
    return myDescriptor;
  }

  public synchronized void registerExtension(@NotNull T extension, @NotNull LoadingOrder order) {
    assert myExtensions.size() == myLoadedAdapters.size();

    if (LoadingOrder.ANY == order) {
      int index = myLoadedAdapters.size();
      if (index > 0) {
        ExtensionComponentAdapter lastAdapter = myLoadedAdapters.get(index - 1);
        if (lastAdapter.getOrder() == LoadingOrder.LAST) {
          index--;
        }
      }
      internalRegisterExtension(extension, new ObjectComponentAdapter(extension, order), index, true);
      clearCache();
    }
    else {
      myExtensionAdapters.add(new ObjectComponentAdapter(extension, order));
      processAdapters();
    }
  }

  private void internalRegisterExtension(T extension, ExtensionComponentAdapter adapter, int index, boolean runNotifications) {
    if (myExtensions.contains(extension)) {
      myLogger.error("Extension was already added: " + extension);
    }
    else {
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

        notifyListenersOnAdd(extension, adapter.getPluginDescriptor());
      }
    }
  }

  private void notifyListenersOnAdd(T extension, final PluginDescriptor pluginDescriptor) {
    for (ExtensionPointListener<T> listener : myEPListeners) {
      try {
        listener.extensionAdded(extension, pluginDescriptor);
      }
      catch (Throwable e) {
        myLogger.error(e);
      }
    }
  }

  @NotNull
  public T[] getExtensions() {
    T[] result = myExtensionsCache;
    if (result == null) {
      synchronized (this) {
        result = myExtensionsCache;
        if (result == null) {
          processAdapters();
          List<T> extensions = new ArrayList<T>(myExtensions);

          List<T> problemExtensions = new ArrayList<T>();

          final Class<T> extensionClass = getExtensionClass();
          for (Iterator<T> iterator = extensions.iterator(); iterator.hasNext();) {
            T t = iterator.next();
            if (!extensionClass.isAssignableFrom(t.getClass())) {
              problemExtensions.add(t);
              iterator.remove();
            }
          }

          for (T problemExtension : problemExtensions) {
            LOG.error("Extension '" + problemExtension.getClass() + "' should be instance of '" + extensionClass + "'", new ExtensionException(problemExtension.getClass()));
          }

          //noinspection unchecked
          myExtensionsCache = result = extensions.toArray((T[])Array.newInstance(extensionClass, extensions.size()));
          for (int i = 1; i < result.length; i++) {
            assert result[i] != result[i - 1] : "Result:      "+ Arrays.asList(result)+";\n" +
                                                " extensions: "+ extensions+";\n" +
                                                " getExtensionClass(): "+ extensionClass +";\n" +
                                                " size:"+extensions.size()+";"+result.length;
          }
        }
      }
    }
    return result;
  }

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
      ExtensionComponentAdapter[] loadedAdapters = myLoadedAdapters.isEmpty() ? ExtensionComponentAdapter.EMPTY_ARRAY : myLoadedAdapters.toArray(new ExtensionComponentAdapter[myLoadedAdapters.size()]);
      myLoadedAdapters.clear();
      ExtensionComponentAdapter[] adapters = allAdapters.toArray(new ExtensionComponentAdapter[myExtensionAdapters.size()]);
      LoadingOrder.sort(adapters);
      for (ExtensionComponentAdapter adapter : adapters) {
        //noinspection unchecked
        T extension = (T)adapter.getExtension();
        internalRegisterExtension(extension, adapter, myExtensions.size(), ArrayUtil.find(loadedAdapters, adapter) == -1);
      }
      myExtensionAdapters.clear();
    }
  }

  @Nullable
  public T getExtension() {
    T[] extensions = getExtensions();
    if (extensions.length == 0) return null;

    return extensions[0];
  }

  public synchronized boolean hasExtension(@NotNull T extension) {
    processAdapters();
    return myExtensions.contains(extension);
  }

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
    clearCache();
  }

  private int getExtensionIndex(@NotNull T extension) {
    int i = myExtensions.indexOf(extension);
    if (i == -1) {
      throw new IllegalArgumentException("Extension to be removed not found: " + extension);
    }
    return i;
  }

  private void internalUnregisterExtension(T extension, PluginDescriptor pluginDescriptor) {
    int index = getExtensionIndex(extension);
    myExtensions.remove(index);

    myLoadedAdapters.remove(index);

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

  private void notifyListenersOnRemove(T extensionObject, PluginDescriptor pluginDescriptor) {
    for (ExtensionPointListener<T> listener : myEPListeners) {
      try {
        listener.extensionRemoved(extensionObject, pluginDescriptor);
      }
      catch (Throwable e) {
        myLogger.error(e);
      }
    }
  }

  public synchronized void addExtensionPointListener(@NotNull ExtensionPointListener<T> listener) {
    processAdapters();
    if (myEPListeners.add(listener)) {
      for (ExtensionComponentAdapter componentAdapter : myLoadedAdapters.toArray(new ExtensionComponentAdapter[myLoadedAdapters.size()])) {
        try {
          //noinspection unchecked
          listener.extensionAdded((T)componentAdapter.getExtension(), componentAdapter.getPluginDescriptor());
        } catch (Throwable e) {
          myLogger.error(e);
        }
      }
    }
  }

  public synchronized void removeExtensionPointListener(@NotNull ExtensionPointListener<T> listener) {
    for (ExtensionComponentAdapter componentAdapter : myLoadedAdapters.toArray(new ExtensionComponentAdapter[myLoadedAdapters.size()])) {
      try {
        //noinspection unchecked
        listener.extensionRemoved((T)componentAdapter.getExtension(), componentAdapter.getPluginDescriptor());
      }
      catch (Throwable e) {
        myLogger.error(e);
      }
    }

    boolean success = myEPListeners.remove(listener);
    assert success;
  }

  public synchronized void reset() {
    myOwner.removeAllComponents(myExtensionAdapters);
    myExtensionAdapters.clear();
    for (T extension : getExtensions()) {
      unregisterExtension(extension);
    }
  }

  public Class<T> getExtensionClass() {
    // racy single-check: we don't care whether the access to 'myExtensionClass' is thread-safe
    // but initial store in a local variable is crucial to prevent instruction reordering
    // see Item 71 in Effective Java or http://jeremymanson.blogspot.com/2008/12/benign-data-races-in-java.html
    Class<T> extensionClass = myExtensionClass;
    if (extensionClass == null) {
      try {
        ClassLoader pluginClassLoader = myDescriptor.getPluginClassLoader();
        //noinspection unchecked
        myExtensionClass = extensionClass = pluginClassLoader == null
                                            ? (Class<T>)Class.forName(myClassName)
                                            : (Class<T>)Class.forName(myClassName, true, pluginClassLoader);
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

  synchronized void registerExtensionAdapter(ExtensionComponentAdapter adapter) {
    myExtensionAdapters.add(adapter);
    clearCache();
  }

  private void clearCache() {
    myExtensionsCache = null;
  }

  synchronized boolean unregisterComponentAdapter(final ExtensionComponentAdapter componentAdapter) {
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

        //noinspection unchecked
        internalUnregisterExtension((T)componentAdapter.getExtension(), componentAdapter.getPluginDescriptor());
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

    private ObjectComponentAdapter(Object extension, LoadingOrder loadingOrder) {
      super(Object.class.getName(), null, null, null, false);
      myExtension = extension;
      myLoadingOrder = loadingOrder;
    }

    public Object getExtension() {
      return myExtension;
    }

    public LoadingOrder getOrder() {
      return myLoadingOrder;
    }

    @Nullable
    public String getOrderId() {
      return null;
    }

    @NonNls
    public Element getDescribingElement() {
      return new Element("RuntimeExtension: " + myExtension);
    }
  }
}
