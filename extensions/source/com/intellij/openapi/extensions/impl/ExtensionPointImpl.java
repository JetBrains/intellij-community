/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.*;
import org.jdom.Element;
import org.picocontainer.MutablePicoContainer;

import java.lang.ref.SoftReference;
import java.lang.reflect.Array;
import java.util.*;

/**
 * @author AKireyev
 */
public class ExtensionPointImpl implements ExtensionPoint {
  private final LogProvider myLogger;

  private String myName;
  private String myBeanClassName;
  private List myExtensions = new ArrayList();
  private List myLoadedAdapters = new ArrayList();
  private Set myExtensionAdapters = new LinkedHashSet();
  private Set myEPListeners = new LinkedHashSet();
  private SoftReference myExtensionsCache;
  private ExtensionsAreaImpl myOwner;
  private final AreaInstance myArea;
  private Class myExtensionClass;
  private PluginDescriptor myDescriptor;

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

  public void registerExtension(Object extension) {
    registerExtension(extension, LoadingOrder.ANY);
  }

  public void registerExtension(Object extension, LoadingOrder order) {
    assert (extension != null) : "Extension cannot be null";

    myOwner.getMutablePicoContainer().registerComponentInstance(new Object(), extension);

    assert myExtensions.size() == myLoadedAdapters.size();

    if (LoadingOrder.ANY == order) {
      int index = myLoadedAdapters.size();
      if (myLoadedAdapters.size() > 0) {
        ExtensionComponentAdapter lastAdapter = (ExtensionComponentAdapter) myLoadedAdapters.get(myLoadedAdapters.size() - 1);
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

  private void internalRegisterExtension(Object extension, ExtensionComponentAdapter adapter, int index, boolean runNotifications) {
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

        notifyListenersOnAdd(extension);
      }
    }
  }

  private void notifyListenersOnAdd(Object extension) {
    ExtensionPointListener[] listeners = (ExtensionPointListener[]) myEPListeners.toArray(new ExtensionPointListener[myEPListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      ExtensionPointListener listener = listeners[i];
      try {
        listener.extensionAdded(extension);
      } catch (Throwable e) {
        myLogger.error(e);
      }
    }
  }

  public Object[] getExtensions() {
    Object[] result = null;

    processAdapters();

    if (myExtensionsCache != null) {
      result = (Object[]) myExtensionsCache.get();
    }
    if (result == null) {
      result = myExtensions.toArray((Object[])Array.newInstance(getExtensionClass(), myExtensions.size()));
      myExtensionsCache = new SoftReference(result);
    }

    return result;
  }

  private void processAdapters() {
    if (myExtensionAdapters.size() > 0) {
      List allAdapters = new ArrayList(myExtensionAdapters.size() + myLoadedAdapters.size());
      allAdapters.addAll(myExtensionAdapters);
      allAdapters.addAll(myLoadedAdapters);
      myExtensions.clear();
      List loadedAdapters = myLoadedAdapters;
      myLoadedAdapters = new ArrayList();
      ExtensionComponentAdapter[] adapters = (ExtensionComponentAdapter[]) allAdapters.toArray(new ExtensionComponentAdapter[myExtensionAdapters.size()]);
      LoadingOrder.sort(adapters);
      for (int i = 0; i < adapters.length; i++) {
        ExtensionComponentAdapter adapter = adapters[i];
        Object extension = adapter.getExtension();
        internalRegisterExtension(extension, adapter, i, !loadedAdapters.contains(adapter));
      }
      myExtensionAdapters.clear();
    }
  }

  public Object getExtension() {
    Object[] extensions = getExtensions();
    if (extensions.length == 0) return null;

    return extensions[0];
  }

  public boolean hasExtension(Object extension) {
    processAdapters();

    return myExtensions.contains(extension);
  }

  public void unregisterExtension(final Object extension) {
    assert (extension != null) : "Extension cannot be null";

    myOwner.getMutablePicoContainer().unregisterComponentByInstance(extension);
    final MutablePicoContainer[] pluginContainers = myOwner.getPluginContainers();
    for (int i = 0; i < pluginContainers.length; i++) {
      MutablePicoContainer pluginContainer = pluginContainers[i];
      pluginContainer.unregisterComponentByInstance(extension);
    }

    processAdapters();

    internalUnregisterExtension(extension);
  }

  private void internalUnregisterExtension(Object extension) {
    myExtensionsCache = null;

    if (!myExtensions.contains(extension)) {
      throw new IllegalArgumentException("Extension to be removed not found: " + extension);
    }
    int index = myExtensions.indexOf(extension);
    myExtensions.remove(index);
    myLoadedAdapters.remove(index);

    notifyListenersOnRemove(extension);

    if (extension instanceof Extension) {
      Extension o = (Extension) extension;
      try {
        o.extensionRemoved(this);
      } catch (Throwable e) {
        myLogger.error(e);
      }
    }
  }

  private void notifyListenersOnRemove(Object extensionObject) {
    for (Iterator iterator = myEPListeners.iterator(); iterator.hasNext();) {
      ExtensionPointListener listener = (ExtensionPointListener)iterator.next();
      try {
        listener.extensionRemoved(extensionObject);
      } catch (Throwable e) {
        myLogger.error(e);
      }
    }
  }

  public void addExtensionPointListener(ExtensionPointListener listener) {
    if (myEPListeners.add(listener)) {
      for (Iterator iterator = myExtensions.iterator(); iterator.hasNext();) {
        try {
          listener.extensionAdded(iterator.next());
        } catch (Throwable e) {
          myLogger.error(e);
        }
      }
    }
  }

  public void removeExtensionPointListener(ExtensionPointListener listener) {
    if (myEPListeners.contains(listener)) {
      for (Iterator iterator = myExtensions.iterator(); iterator.hasNext();) {
        try {
          listener.extensionRemoved(iterator.next());
        } catch (Throwable e) {
          myLogger.error(e);
        }
      }

      myEPListeners.remove(listener);
    }
  }

  public void reset() {
    myOwner.removeAllComponents(myExtensionAdapters);
    myExtensionAdapters.clear();
    Object[] extensions = getExtensions();
    for (int i = 0; i < extensions.length; i++) {
      Object extension = extensions[i];
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

  void registerExtensionAdapter(ExtensionComponentAdapter adapter) {
    myExtensionAdapters.add(adapter);
  }

  public boolean unregisterComponentAdapter(final ExtensionComponentAdapter componentAdapter) {
    if (myExtensionAdapters.contains(componentAdapter)) {
      myExtensionAdapters.remove(componentAdapter);
      return true;
    }
    else if (myLoadedAdapters.contains(componentAdapter)) {
      final Object componentKey = componentAdapter.getComponentKey();
      myOwner.getMutablePicoContainer().unregisterComponent(componentKey);
      final MutablePicoContainer[] pluginContainers = myOwner.getPluginContainers();
      for (int i = 0; i < pluginContainers.length; i++) {
        MutablePicoContainer pluginContainer = pluginContainers[i];
        pluginContainer.unregisterComponent(componentKey);
      }

      internalUnregisterExtension(componentAdapter.getExtension());
      return true;
    }
    return false;
  }

  private static class ObjectComponentAdapter extends ExtensionComponentAdapter {
    private Object myExtension;
    private LoadingOrder myLoadingOrder;

    public ObjectComponentAdapter(Object extension, LoadingOrder loadingOrder) {
      super(Object.class, null, null, null);
      myExtension = extension;
      myLoadingOrder = loadingOrder;
    }

    public Object getExtension() {
      return myExtension;
    }

    public LoadingOrder getOrder() {
      return myLoadingOrder;
    }

    public String getOrderId() {
      return null;
    }

    public Element getDescribingElement() {
      return new Element("RuntimeExtension: " + myExtension);
    }
  }
}
