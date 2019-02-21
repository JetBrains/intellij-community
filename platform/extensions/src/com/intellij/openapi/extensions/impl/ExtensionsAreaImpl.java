// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.pico.CachingConstructorInjectionComponentAdapter;
import com.intellij.util.pico.DefaultPicoContainer;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.PicoContainer;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("HardCodedStringLiteral")
public final class ExtensionsAreaImpl implements ExtensionsArea {
  private static final Logger LOG = Logger.getInstance(ExtensionsAreaImpl.class);
  public static final String ATTRIBUTE_AREA = "area";

  private static final boolean DEBUG_REGISTRATION = Boolean.FALSE.booleanValue(); // not compile-time constant to avoid yellow code

  private final AreaPicoContainer myPicoContainer;
  private final Map<String, ExtensionPointImpl> myExtensionPoints = ContainerUtil.newConcurrentMap();
  private final Map<String,Throwable> myEPTraces = DEBUG_REGISTRATION ? new THashMap<>() : null;
  private final MultiMap<String, ExtensionPointAvailabilityListener> myAvailabilityListeners = MultiMap.createSmart(); // guarded by myAvailabilityListeners
  private final AreaInstance myAreaInstance;
  private final String myAreaClass;

  public ExtensionsAreaImpl(@Nullable String areaClass, @Nullable AreaInstance areaInstance, PicoContainer parentPicoContainer) {
    myAreaClass = areaClass;
    myAreaInstance = areaInstance;
    myPicoContainer = new DefaultPicoContainer(parentPicoContainer);
    initialize();
  }

  @Nullable
  AreaInstance getAreaInstance() {
    return myAreaInstance;
  }

  @TestOnly
  public final void notifyAreaReplaced(@NotNull ExtensionsAreaImpl newArea) {
    Set<String> processedEPs = ContainerUtil.newTroveSet();
    for (final ExtensionPointImpl point : myExtensionPoints.values()) {
      point.notifyAreaReplaced(this);
      processedEPs.add(point.getName());
    }
    // this code is required because we have a lot of static extensions e.g. LanguageExtension that are initialized only once
    // for the extensions AvailabilityListeners will be broken if the initialization happened in "fake" area which doesn't have required EP
    if (!myAvailabilityListeners.isEmpty()) {
      for (Map.Entry<String, Collection<ExtensionPointAvailabilityListener>> entry : myAvailabilityListeners.entrySet()) {
        String key = entry.getKey();
        if (!processedEPs.contains(key)) {
          boolean wasAdded = false;
          //if listeners are "detached" for any EP we have to transfer them to the new area (otherwise it will affect area searching)
          for (ExtensionPointAvailabilityListener listener : entry.getValue()) {
            if (!newArea.hasAvailabilityListener(key, listener)) {
              newArea.addAvailabilityListener(key, listener, null);
              wasAdded = true;
            }
          }
          if (wasAdded) {
            processedEPs.add(key);
          }
        }
      }
    }

    for (ExtensionPointImpl point : newArea.myExtensionPoints.values()) {
      if (!processedEPs.contains(point.getName())) {
        point.notifyAreaReplaced(this);
      }
    }
  }

  @NotNull
  @Override
  public AreaPicoContainer getPicoContainer() {
    return myPicoContainer;
  }

  @Override
  public String getAreaClass() {
    return myAreaClass;
  }

  @Override
  public void registerExtensionPoint(@NotNull PluginDescriptor pluginDescriptor, @NotNull Element extensionPointElement) {
    assert pluginDescriptor.getPluginId() != null;
    final String pluginId = pluginDescriptor.getPluginId().getIdString();
    String pointName = extensionPointElement.getAttributeValue("qualifiedName");
    if (pointName == null) {
      final String name = extensionPointElement.getAttributeValue("name");
      if (name == null) {
        throw new RuntimeException("'name' attribute not specified for extension point in '" + pluginId + "' plugin");
      }
      pointName = pluginId + '.' + name;
    }

    String beanClassName = extensionPointElement.getAttributeValue("beanClass");
    String interfaceClassName = extensionPointElement.getAttributeValue("interface");
    if (beanClassName == null && interfaceClassName == null) {
      throw new RuntimeException("Neither 'beanClass' nor 'interface' attribute is specified for extension point '" + pointName + "' in '" + pluginId + "' plugin");
    }

    if (beanClassName != null && interfaceClassName != null) {
      throw new RuntimeException("Both 'beanClass' and 'interface' attributes are specified for extension point '" + pointName + "' in '" + pluginId + "' plugin");
    }

    ExtensionPointImpl<Object> point;
    if (interfaceClassName == null) {
      point = new BeanExtensionPoint<>(pointName, beanClassName, this, pluginDescriptor);
    }
    else {
      point = new InterfaceExtensionPoint<>(pointName, interfaceClassName, this, pluginDescriptor);
    }
    registerExtensionPoint(point);
  }

  @Override
  public void registerExtension(@NotNull final PluginDescriptor pluginDescriptor, @NotNull final Element extensionElement, String extensionNs) {
    String epName = extractPointName(extensionElement, extensionNs);
    registerExtension(getExtensionPoint(epName), pluginDescriptor, extensionElement);
  }

  // Used in Upsource
  @Override
  public void registerExtension(@NotNull final ExtensionPoint extensionPoint, @NotNull final PluginDescriptor pluginDescriptor, @NotNull final Element extensionElement) {
    ((ExtensionPointImpl)extensionPoint).createAndRegisterAdapter(extensionElement, pluginDescriptor);
  }

  @NotNull
  public static String extractPointName(@NotNull Element extensionElement, @Nullable String ns) {
    String epName = extensionElement.getAttributeValue("point");
    if (epName == null) {
      if (ns == null) {
        Namespace namespace = extensionElement.getNamespace();
        epName = namespace.getURI() + '.' + extensionElement.getName();
      }
      else {
        epName = ns + '.' + extensionElement.getName();
      }
    }
    return epName;
  }

  private void initialize() {
    InterfaceExtensionPoint<EPAvailabilityListenerExtension> point =
      new InterfaceExtensionPoint<>(EPAvailabilityListenerExtension.EXTENSION_POINT_NAME, EPAvailabilityListenerExtension.class, this);
    registerExtensionPoint(point);
    point.addExtensionPointListener(new ExtensionPointListener<EPAvailabilityListenerExtension>() {
      @Override
      public void extensionRemoved(@NotNull EPAvailabilityListenerExtension extension, @Nullable PluginDescriptor pluginDescriptor) {
        synchronized (myAvailabilityListeners) {
          Collection<ExtensionPointAvailabilityListener> listeners = myAvailabilityListeners.get(extension.getExtensionPointName());
          for (Iterator<ExtensionPointAvailabilityListener> iterator = listeners.iterator(); iterator.hasNext(); ) {
            ExtensionPointAvailabilityListener listener = iterator.next();
            if (listener.getClass().getName().equals(extension.getListenerClass())) {
              iterator.remove();
              return;
            }
          }
        }
        LOG.warn("Failed to find EP availability listener: " + extension.getListenerClass());
      }

      @Override
      public void extensionAdded(@NotNull EPAvailabilityListenerExtension extension, @Nullable PluginDescriptor pluginDescriptor) {
        String epName = extension.getExtensionPointName();

        ExtensionPointAvailabilityListener listener;
        try {
          listener = (ExtensionPointAvailabilityListener)instantiate(extension.loadListenerClass());
        }
        catch (ClassNotFoundException e) {
          throw new RuntimeException(e);
        }

        addAvailabilityListener(epName, listener, null);
      }
    }, false, null);
  }

  @NotNull
  private Object instantiate(@NotNull Class clazz) {
    CachingConstructorInjectionComponentAdapter adapter =
      new CachingConstructorInjectionComponentAdapter(Integer.toString(System.identityHashCode(new Object())), clazz);
    return adapter.getComponentInstance(getPicoContainer());
  }

  @Override
  public void addAvailabilityListener(@NotNull String extensionPointName, @NotNull ExtensionPointAvailabilityListener listener, @Nullable Disposable parentDisposable) {
    synchronized (myAvailabilityListeners) {
      myAvailabilityListeners.putValue(extensionPointName, listener);
    }
    ExtensionPointImpl<?> ep = myExtensionPoints.get(extensionPointName);
    if (ep != null) {
      listener.extensionPointRegistered(ep);
    }

    if (parentDisposable != null) {
      Disposer.register(parentDisposable, new Disposable() {
        @Override
        public void dispose() {
          removeAvailabilityListener(extensionPointName, listener);
        }
      });
    }
  }

  @Override
  public void removeAvailabilityListener(@NotNull String extensionPointName, @NotNull ExtensionPointAvailabilityListener listener) {
    synchronized (myAvailabilityListeners) {
      myAvailabilityListeners.remove(extensionPointName, listener);
    }
  }

  private boolean hasAvailabilityListener(@NotNull String extensionPointName, @NotNull ExtensionPointAvailabilityListener listener) {
    Collection<ExtensionPointAvailabilityListener> listeners = myAvailabilityListeners.get(extensionPointName);
    return ContainerUtil.containsIdentity(listeners, listener);
  }

  @Override
  public void registerExtensionPoint(@NotNull @NonNls String extensionPointName,
                                     @NotNull String extensionPointBeanClass,
                                     @NotNull ExtensionPoint.Kind kind) {
    doRegisterExtensionPoint(extensionPointName, extensionPointBeanClass, kind);
  }

  @Override
  public void registerExtensionPoint(@NotNull BaseExtensionPointName extensionPoint,
                                     @NotNull String extensionPointBeanClass,
                                     @NotNull ExtensionPoint.Kind kind,
                                     @NotNull Disposable parentDisposable) {
    String extensionPointName = extensionPoint.getName();
    doRegisterExtensionPoint(extensionPointName, extensionPointBeanClass, kind);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        unregisterExtensionPoint(extensionPointName);
      }
    });
  }

  void doRegisterExtensionPoint(@NotNull String extensionPointName, @NotNull String extensionPointBeanClass, @NotNull ExtensionPoint.Kind kind) {
    PluginDescriptor pluginDescriptor = new UndefinedPluginDescriptor();
    ExtensionPointImpl<Object> point;
    if (kind == ExtensionPoint.Kind.INTERFACE) {
      point = new InterfaceExtensionPoint<>(extensionPointName, extensionPointBeanClass, this, pluginDescriptor);
    }
    else {
      point = new BeanExtensionPoint<>(extensionPointName, extensionPointBeanClass, this, pluginDescriptor);
    }
    registerExtensionPoint(point);
  }

  @Nullable
  private static PluginId extractPluginId(@NotNull PluginDescriptor descriptor) {
    return descriptor instanceof UndefinedPluginDescriptor ? null : descriptor.getPluginId();
  }

  private void checkThatPointNotDuplicated(@NotNull String pointName, @NotNull PluginDescriptor pluginDescriptor) {
    if (!hasExtensionPoint(pointName)) {
      return;
    }

    String message = "Duplicate registration for EP: " + pointName + ": original plugin " +
                     extractPluginId(getExtensionPoint(pointName).getDescriptor()) +
                     ", new plugin " + extractPluginId(pluginDescriptor);
    if (DEBUG_REGISTRATION) {
      LOG.error(message, myEPTraces.get(pointName));
    }
    throw new PicoPluginExtensionInitializationException(message, null, extractPluginId(pluginDescriptor));
  }

  public void registerExtensionPoint(@NotNull ExtensionPointImpl<?> point) {
    String name = point.getName();
    checkThatPointNotDuplicated(name, point.getDescriptor());
    myExtensionPoints.put(name, point);
    notifyPointRegistered(point);
    if (DEBUG_REGISTRATION) {
      myEPTraces.put(name, new Throwable("Original registration for " + name));
    }
  }

  private void notifyPointRegistered(@NotNull ExtensionPoint extensionPoint) {
    Collection<ExtensionPointAvailabilityListener> listeners;
    synchronized (myAvailabilityListeners) {
      listeners = myAvailabilityListeners.get(extensionPoint.getName());
    }
    for (final ExtensionPointAvailabilityListener listener : listeners) {
      listener.extensionPointRegistered(extensionPoint);
    }
  }

  @Override
  @NotNull
  public <T> ExtensionPointImpl<T> getExtensionPoint(@NotNull String extensionPointName) {
    //noinspection unchecked
    ExtensionPointImpl<T> extensionPoint = myExtensionPoints.get(extensionPointName);
    if (extensionPoint == null) {
      throw new IllegalArgumentException("Missing extension point: " + extensionPointName + " in area " + myAreaInstance);
    }
    return extensionPoint;
  }

  @NotNull
  @Override
  public <T> ExtensionPoint<T> getExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName) {
    return getExtensionPoint(extensionPointName.getName());
  }

  @NotNull
  @Override
  public ExtensionPoint[] getExtensionPoints() {
    return myExtensionPoints.values().toArray(new ExtensionPoint[0]);
  }

  @Override
  public void unregisterExtensionPoint(@NotNull final String extensionPointName) {
    ExtensionPoint extensionPoint = myExtensionPoints.get(extensionPointName);
    if (extensionPoint != null) {
      extensionPoint.reset();
      myExtensionPoints.remove(extensionPointName);
      notifyEPRemoved(extensionPoint);
    }
  }

  private void notifyEPRemoved(@NotNull ExtensionPoint extensionPoint) {
    Collection<ExtensionPointAvailabilityListener> listeners;
    synchronized (myAvailabilityListeners) {
      listeners = myAvailabilityListeners.get(extensionPoint.getName());
    }
    for (final ExtensionPointAvailabilityListener listener : listeners) {
      listener.extensionPointRemoved(extensionPoint);
    }
  }

  @Override
  public boolean hasExtensionPoint(@NotNull String extensionPointName) {
    return myExtensionPoints.containsKey(extensionPointName);
  }

  @Override
  public boolean hasExtensionPoint(@NotNull ExtensionPointName<?> extensionPointName) {
    return hasExtensionPoint(extensionPointName.getName());
  }

  @Override
  public String toString() {
    return (myAreaClass == null ? "Root" : myAreaClass)+" Area";
  }
}
