/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.MutablePicoContainer;

import java.lang.ref.SoftReference;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author AKireyev
 */
public class ExtensionPointImpl<T> implements ExtensionPoint<T> {
  private final LogProvider myLogger;

  private final AreaInstance myArea;
  private final String myName;
  private final String myBeanClassName;
  private final List<T> myExtensions = new ArrayList<T>();
  private final ExtensionsAreaImpl myOwner;
  private final PluginDescriptor myDescriptor;
  private final Set<ExtensionComponentAdapter> myExtensionAdapters = new LinkedHashSet<ExtensionComponentAdapter>();

  private final Set<ExtensionPointListener<T>> myEPListeners = new LinkedHashSet<ExtensionPointListener<T>>();

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private List<ExtensionComponentAdapter> myLoadedAdapters = new CopyOnWriteArrayList<ExtensionComponentAdapter>();

  private SoftReference<T[]> myExtensionsCache;

  private Class myExtensionClass;

  public ExtensionPointImpl(String name,
                            String beanClassName,
                            ExtensionsAreaImpl owner,
                            AreaInstance area,
                            LogProvider logger,
                            PluginDescriptor descriptor) {
    myName = name;
    myBeanClassName = beanClassName;
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
    return myBeanClassName;
  }

  public void registerExtension(T extension) {
    registerExtension(extension, LoadingOrder.ANY);
  }

  public PluginDescriptor getDescriptor() {
    return myDescriptor;
  }

  public synchronized void registerExtension(T extension, LoadingOrder order) {
    assert (extension != null) : "Extension cannot be null";

    assert myExtensions.size() == myLoadedAdapters.size();

    if (LoadingOrder.ANY == order) {
      int index = myLoadedAdapters.size();
      if (myLoadedAdapters.size() > 0) {
        ExtensionComponentAdapter lastAdapter = myLoadedAdapters.get(myLoadedAdapters.size() - 1);
        if (lastAdapter.getOrder() == LoadingOrder.LAST) {
          index--;
        }
      }
      internalRegisterExtension(extension, new ObjectComponentAdapter(extension, order), index, true);
    }
    else {
      myExtensionAdapters.add(new ObjectComponentAdapter(extension, order));
      processAdapters();
    }
  }

  private void internalRegisterExtension(T extension, ExtensionComponentAdapter adapter, int index, boolean runNotifications) {
    myExtensionsCache = null;

    if (myExtensions.contains(extension)) {
      myLogger.error("Extension was already added: " + extension);
    }
    else {
      myExtensions.add(index, extension);
      myLoadedAdapters.add(index, adapter);
      if (runNotifications) {
        if (extension instanceof Extension) {
          Extension o = (Extension) extension;
          try {
            o.extensionAdded(this);
          } catch (Throwable e) {
            myLogger.error(e);
          }
        }

        notifyListenersOnAdd(extension, adapter.getPluginDescriptor());
      }
    }
  }

  private void notifyListenersOnAdd(T extension, final PluginDescriptor pluginDescriptor) {
    //noinspection unchecked
    for (ExtensionPointListener<T> listener : getListenersCopy()) {
      try {
        listener.extensionAdded(extension, pluginDescriptor);
      }
      catch (Throwable e) {
        myLogger.error(e);
      }
    }
  }

  private ExtensionPointListener<T>[] getListenersCopy() {
    return myEPListeners.toArray(new ExtensionPointListener[myEPListeners.size()]);
  }

  @NotNull
  public T[] getExtensions() {
    T[] result = null;

    processAdapters();

    //noinspection SynchronizeOnThis
    synchronized (this) {
      if (myExtensionsCache != null) {
        result = myExtensionsCache.get();
      }
      if (result == null) {
        //noinspection unchecked
        result = myExtensions.toArray((T[])Array.newInstance(getExtensionClass(), myExtensions.size()));
        myExtensionsCache = new SoftReference<T[]>(result);
      }
    }

    return result;
  }

  private synchronized void processAdapters() {
    if (myExtensionAdapters.size() > 0) {
      List<ExtensionComponentAdapter> allAdapters = new ArrayList<ExtensionComponentAdapter>(myExtensionAdapters.size() + myLoadedAdapters.size());
      allAdapters.addAll(myExtensionAdapters);
      allAdapters.addAll(myLoadedAdapters);
      myExtensions.clear();
      List<ExtensionComponentAdapter> loadedAdapters = myLoadedAdapters;
      myLoadedAdapters = new ArrayList<ExtensionComponentAdapter>();
      ExtensionComponentAdapter[] adapters = allAdapters.toArray(new ExtensionComponentAdapter[myExtensionAdapters.size()]);
      LoadingOrder.sort(adapters);
      for (int i = 0; i < adapters.length; i++) {
        ExtensionComponentAdapter adapter = adapters[i];

        //noinspection unchecked
        T extension = (T)adapter.getExtension();
        internalRegisterExtension(extension, adapter, i, !loadedAdapters.contains(adapter));
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

  public synchronized boolean hasExtension(T extension) {
    processAdapters();

    return myExtensions.contains(extension);
  }

  public synchronized void unregisterExtension(final T extension) {
    assert (extension != null) : "Extension cannot be null";
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

  private int getExtensionIndex(final T extension) {
    if (!myExtensions.contains(extension)) {
      throw new IllegalArgumentException("Extension to be removed not found: " + extension);
    }

    return myExtensions.indexOf(extension);
  }

  private synchronized void internalUnregisterExtension(T extension, PluginDescriptor pluginDescriptor) {
    myExtensionsCache = null;

    int index = getExtensionIndex(extension);
    myExtensions.remove(index);
    myLoadedAdapters.remove(index);

    notifyListenersOnRemove(extension, pluginDescriptor);

    if (extension instanceof Extension) {
      Extension o = (Extension) extension;
      try {
        o.extensionRemoved(this);
      } catch (Throwable e) {
        myLogger.error(e);
      }
    }
  }

  private void notifyListenersOnRemove(T extensionObject, PluginDescriptor pluginDescriptor) {
    for (ExtensionPointListener<T> listener : getListenersCopy()) {
      try {
        listener.extensionRemoved(extensionObject, pluginDescriptor);
      }
      catch (Throwable e) {
        myLogger.error(e);
      }
    }
  }

  public void addExtensionPointListener(ExtensionPointListener<T> listener) {
    processAdapters();

    if (myEPListeners.add(listener)) {
      for (ExtensionComponentAdapter componentAdapter : myLoadedAdapters) {
        try {
          //noinspection unchecked
          listener.extensionAdded((T)componentAdapter.getExtension(), componentAdapter.getPluginDescriptor());
        } catch (Throwable e) {
          myLogger.error(e);
        }
      }
    }
  }

  public void removeExtensionPointListener(ExtensionPointListener<T> listener) {
    if (myEPListeners.contains(listener)) {
      for (ExtensionComponentAdapter componentAdapter : myLoadedAdapters) {
        try {
          //noinspection unchecked
          listener.extensionRemoved((T)componentAdapter.getExtension(), componentAdapter.getPluginDescriptor());
        } catch (Throwable e) {
          myLogger.error(e);
        }
      }

      myEPListeners.remove(listener);
    }
  }

  public synchronized void reset() {
    myOwner.removeAllComponents(myExtensionAdapters);
    myExtensionAdapters.clear();
    for (T extension : getExtensions()) {
      unregisterExtension(extension);
    }
  }

  public Class getExtensionClass() {
    if (myExtensionClass == null) {
      try {
        if (myDescriptor.getPluginClassLoader() == null) {
          myExtensionClass = Class.forName(myBeanClassName);
        }
        else {
          myExtensionClass = Class.forName(myBeanClassName, true, myDescriptor.getPluginClassLoader());
        }
      }
      catch (ClassNotFoundException e) {
        myExtensionClass = Object.class;
      }
    }
    return myExtensionClass;
  }

  public String toString() {
    return getName();
  }

  synchronized void registerExtensionAdapter(ExtensionComponentAdapter adapter) {
    myExtensionAdapters.add(adapter);
  }

  public synchronized boolean unregisterComponentAdapter(final ExtensionComponentAdapter componentAdapter) {
    if (myExtensionAdapters.contains(componentAdapter)) {
      myExtensionAdapters.remove(componentAdapter);
      return true;
    }
    else if (myLoadedAdapters.contains(componentAdapter)) {
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

  @TestOnly
  final void dropCaches() {
    for (final ExtensionPointListener<T> listener : getListenersCopy()) {
      if (listener instanceof SmartExtensionPoint) {
        ((SmartExtensionPoint)listener).dropCache();
      }
    }
  }

  private static class ObjectComponentAdapter extends ExtensionComponentAdapter {
    private Object myExtension;
    private LoadingOrder myLoadingOrder;

    public ObjectComponentAdapter(Object extension, LoadingOrder loadingOrder) {
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
