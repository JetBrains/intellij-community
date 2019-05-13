// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.pico.CachingConstructorInjectionComponentAdapter;
import com.intellij.util.pico.DefaultPicoContainer;
import gnu.trove.THashMap;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("HardCodedStringLiteral")
public class ExtensionsAreaImpl implements ExtensionsArea {
  private static final Logger LOG = Logger.getInstance(ExtensionsAreaImpl.class);
  public static final String ATTRIBUTE_AREA = "area";

  private static final boolean DEBUG_REGISTRATION = Boolean.FALSE.booleanValue(); // not compile-time constant to avoid yellow code

  private final AreaPicoContainer myPicoContainer;
  private final Map<String, ExtensionPointImpl> myExtensionPoints = ContainerUtil.newConcurrentMap();
  private final Map<String,Throwable> myEPTraces = DEBUG_REGISTRATION ? new THashMap<>() : null;
  private final MultiMap<String, ExtensionPointAvailabilityListener> myAvailabilityListeners = MultiMap.createSmart(); // guarded by myAvailabilityListeners
  private final AreaInstance myAreaInstance;
  private final String myAreaClass;

  public ExtensionsAreaImpl(String areaClass, AreaInstance areaInstance, PicoContainer parentPicoContainer) {
    myAreaClass = areaClass;
    myAreaInstance = areaInstance;
    myPicoContainer = new DefaultPicoContainer(parentPicoContainer);
    initialize();
  }

  @TestOnly
  public final void notifyAreaReplaced(@NotNull ExtensionsAreaImpl newArea) {
    Set<String> processedEPs = ContainerUtil.newTroveSet();
    for (final ExtensionPointImpl point : myExtensionPoints.values()) {
      point.notifyAreaReplaced(this);
      processedEPs.add(point.getName());
    }
    //this code is required because we have a lot of static extensions e.g. LanguageExtension that are initialized only once
    //for the extensions AvailabilityListeners will be broken if the initialization happened in "fake" area which doesn't have required EP
    if (!myAvailabilityListeners.isEmpty()) {
      for (Map.Entry<String, Collection<ExtensionPointAvailabilityListener>> entry : myAvailabilityListeners.entrySet()) {
        String key = entry.getKey();
        if (!processedEPs.contains(key)) {
          boolean wasAdded = false;
          //if listeners are "detached" for any EP we have to transfer them to the new area (otherwise it will affect area searching)
          for (ExtensionPointAvailabilityListener listener : entry.getValue()) {
            if (!newArea.hasAvailabilityListener(key, listener)) {
              newArea.addAvailabilityListener(key, listener);
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
    String epName = extensionPointElement.getAttributeValue("qualifiedName");
    if (epName == null) {
      final String name = extensionPointElement.getAttributeValue("name");
      if (name == null) {
        throw new RuntimeException("'name' attribute not specified for extension point in '" + pluginId + "' plugin");
      }
      epName = pluginId + '.' + name;
    }

    String beanClassName = extensionPointElement.getAttributeValue("beanClass");
    String interfaceClassName = extensionPointElement.getAttributeValue("interface");
    if (beanClassName == null && interfaceClassName == null) {
      throw new RuntimeException("Neither 'beanClass' nor 'interface' attribute is specified for extension point '" + epName + "' in '" + pluginId + "' plugin");
    }
    if (beanClassName != null && interfaceClassName != null) {
      throw new RuntimeException("Both 'beanClass' and 'interface' attributes are specified for extension point '" + epName + "' in '" + pluginId + "' plugin");
    }

    ExtensionPoint.Kind kind;
    String className;
    if (interfaceClassName != null) {
      className = interfaceClassName;
      kind = ExtensionPoint.Kind.INTERFACE;
    }
    else {
      className = beanClassName;
      kind = ExtensionPoint.Kind.BEAN_CLASS;
    }
    registerExtensionPoint(epName, className, pluginDescriptor, kind);
  }

  @Override
  public void registerExtension(@NotNull final PluginDescriptor pluginDescriptor, @NotNull final Element extensionElement, String extensionNs) {
    String epName = extractEPName(extensionElement, extensionNs);
    registerExtension(getExtensionPoint(epName), pluginDescriptor, extensionElement);
  }

  // Used in Upsource
  @Override
  public void registerExtension(@NotNull final ExtensionPoint extensionPoint, @NotNull final PluginDescriptor pluginDescriptor, @NotNull final Element extensionElement) {
    ExtensionComponentAdapter adapter;
    if (extensionPoint.getKind() == ExtensionPoint.Kind.INTERFACE) {
      String implClass = extensionElement.getAttributeValue("implementation");
      if (implClass == null) {
        throw new RuntimeException("'implementation' attribute not specified for '" + extensionPoint.getName() + "' extension in '"
                                   + pluginDescriptor.getPluginId().getIdString() + "' plugin");
      }
      adapter = createAdapter(implClass, extensionElement, shouldDeserializeInstance(extensionElement), pluginDescriptor);
    }
    else {
      adapter = createAdapter(extensionPoint.getClassName(), extensionElement, !JDOMUtil.isEmpty(extensionElement), pluginDescriptor);
    }
    myPicoContainer.registerComponent(adapter);
    ((ExtensionPointImpl)extensionPoint).registerExtensionAdapter(adapter);
  }

  // this method is not ExtensionComponentAdapter constructor because later ExtensionComponentAdapter will not hold element
  @NotNull
  private ExtensionComponentAdapter createAdapter(@NotNull String implementationClassName, @NotNull Element extensionElement, boolean isNeedToDeserialize, @NotNull PluginDescriptor pluginDescriptor) {
    String orderId = extensionElement.getAttributeValue("id");
    LoadingOrder order = LoadingOrder.readOrder(extensionElement.getAttributeValue("order"));
    return new ExtensionComponentAdapter(implementationClassName, myPicoContainer, pluginDescriptor, orderId, order, isNeedToDeserialize ? extensionElement : null);
  }

  private static boolean shouldDeserializeInstance(@NotNull Element extensionElement) {
    // has content
    if (!extensionElement.getContent().isEmpty()) return true;
    // has custom attributes
    for (Attribute attribute : extensionElement.getAttributes()) {
      final String name = attribute.getName();
      if (!"implementation".equals(name) && !"id".equals(name) && !"order".equals(name) && !"os".equals(name)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public static String extractEPName(@NotNull Element extensionElement, @Nullable String ns) {
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

  private MutablePicoContainer internalGetPluginContainer() {
    return myPicoContainer;
  }

  private void initialize() {
    ExtensionPointImpl<EPAvailabilityListenerExtension> point =
      registerExtensionPoint(EPAvailabilityListenerExtension.EXTENSION_POINT_NAME, EPAvailabilityListenerExtension.class.getName(),
                             new UndefinedPluginDescriptor(), ExtensionPoint.Kind.INTERFACE);
    point.addExtensionPointListener(new ExtensionPointListener<EPAvailabilityListenerExtension>() {
      @Override
      public void extensionRemoved(@NotNull EPAvailabilityListenerExtension extension, @Nullable PluginDescriptor pluginDescriptor) {
        synchronized (myAvailabilityListeners) {
          Collection<ExtensionPointAvailabilityListener> listeners = myAvailabilityListeners.get(extension.getExtensionPointName());
          for (Iterator<ExtensionPointAvailabilityListener> iterator = listeners.iterator(); iterator.hasNext();) {
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
        try {
          String epName = extension.getExtensionPointName();

          ExtensionPointAvailabilityListener listener = (ExtensionPointAvailabilityListener) instantiate(extension.loadListenerClass());
          addAvailabilityListener(epName, listener);
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  @NotNull
  private Object instantiate(@NotNull Class clazz) {
    CachingConstructorInjectionComponentAdapter adapter =
      new CachingConstructorInjectionComponentAdapter(Integer.toString(System.identityHashCode(new Object())), clazz);

    return adapter.getComponentInstance(getPicoContainer());
  }

  @Override
  public void addAvailabilityListener(@NotNull String extensionPointName, @NotNull ExtensionPointAvailabilityListener listener) {
    synchronized (myAvailabilityListeners) {
      myAvailabilityListeners.putValue(extensionPointName, listener);
    }
    ExtensionPointImpl<?> ep = myExtensionPoints.get(extensionPointName);
    if (ep != null) {
      listener.extensionPointRegistered(ep);
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
  public void registerExtensionPoint(@NotNull final String extensionPointName, @NotNull String extensionPointBeanClass) {
    registerExtensionPoint(extensionPointName, extensionPointBeanClass, ExtensionPoint.Kind.INTERFACE);
  }

  @Override
  public void registerExtensionPoint(@NotNull @NonNls String extensionPointName, @NotNull String extensionPointBeanClass, @NotNull ExtensionPoint.Kind kind) {
    registerExtensionPoint(extensionPointName, extensionPointBeanClass, new UndefinedPluginDescriptor(), kind);
  }

  @NotNull
  private <T> ExtensionPointImpl<T> registerExtensionPoint(@NotNull String extensionPointName,
                                                           @NotNull String extensionPointBeanClass,
                                                           @NotNull PluginDescriptor descriptor,
                                                           @NotNull ExtensionPoint.Kind kind) {
    if (hasExtensionPoint(extensionPointName)) {
      final String message =
        "Duplicate registration for EP: " + extensionPointName + ": original plugin " +
        extractPluginId(getExtensionPoint(extensionPointName).getDescriptor()) +
        ", new plugin " + extractPluginId(descriptor);
      if (DEBUG_REGISTRATION) {
        LOG.error(message, myEPTraces.get(extensionPointName));
      }
      throw new PicoPluginExtensionInitializationException(message, null, extractPluginId(descriptor));
    }

    ExtensionPointImpl<T> point = new ExtensionPointImpl<>(extensionPointName, extensionPointBeanClass, kind, this, myAreaInstance, descriptor);
    registerExtensionPoint(point);
    return point;
  }

  private static PluginId extractPluginId(@NotNull PluginDescriptor descriptor) {
    return descriptor instanceof UndefinedPluginDescriptor ? null : descriptor.getPluginId();
  }

  private void registerExtensionPoint(@NotNull ExtensionPointImpl<?> extensionPoint) {
    String name = extensionPoint.getName();
    myExtensionPoints.put(name, extensionPoint);
    notifyEPRegistered(extensionPoint);
    if (DEBUG_REGISTRATION) {
      myEPTraces.put(name, new Throwable("Original registration for " + name));
    }
  }

  private void notifyEPRegistered(@NotNull ExtensionPoint extensionPoint) {
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

  void removeAllComponents(@NotNull Set<ExtensionComponentAdapter> extensionAdapters) {
    for (final Object extensionAdapter : extensionAdapters) {
      ExtensionComponentAdapter componentAdapter = (ExtensionComponentAdapter)extensionAdapter;
      internalGetPluginContainer().unregisterComponent(componentAdapter.getComponentKey());
    }
  }

  @Override
  public String toString() {
    return (myAreaClass == null ? "Root" : myAreaClass)+" Area";
  }
}
