// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.CollectionFactory;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Consumer;

@ApiStatus.Internal
public final class ExtensionsAreaImpl implements ExtensionsArea {
  private static final Logger LOG = Logger.getInstance(ExtensionsAreaImpl.class);

  private static final boolean DEBUG_REGISTRATION = false;

  private final ComponentManager componentManager;
  public volatile Map<String, ExtensionPointImpl<?>> extensionPoints = Collections.emptyMap();
  private final Map<String, Throwable> epTraces = DEBUG_REGISTRATION ? CollectionFactory.createSmallMemoryFootprintMap() : null;

  public ExtensionsAreaImpl(@NotNull ComponentManager componentManager) {
    this.componentManager = componentManager;
  }

  @TestOnly
  public void notifyAreaReplaced(@Nullable ExtensionsAreaImpl newArea) {
    Set<String> processedEPs = new HashSet<>(extensionPoints.size());
    for (ExtensionPointImpl<?> point : extensionPoints.values()) {
      point.notifyAreaReplaced(this);
      processedEPs.add(point.getName());
    }

    if (newArea == null) {
      return;
    }

    for (ExtensionPointImpl<?> point : newArea.extensionPoints.values()) {
      if (!processedEPs.contains(point.getName())) {
        point.notifyAreaReplaced(this);
      }
    }
  }

  @TestOnly
  public void registerExtensionPoints(@NotNull PluginDescriptor pluginDescriptor, @NotNull List<? extends Element> extensionPointElements) {
    for (Element element : extensionPointElements) {
      String pointName = element.getAttributeValue("qualifiedName");
      if (pointName == null) {
        String name = element.getAttributeValue("name");
        if (name == null) {
          throw componentManager.createError("'name' attribute not specified for extension point in '" + pluginDescriptor + "' plugin",
                                             pluginDescriptor.getPluginId());
        }
        pointName = pluginDescriptor.getPluginId().getIdString() + '.' + name;
      }

      String beanClassName = element.getAttributeValue("beanClass");
      String interfaceClassName = element.getAttributeValue("interface");
      if (beanClassName == null && interfaceClassName == null) {
        throw componentManager.createError("Neither 'beanClass' nor 'interface' attribute is specified for extension point '" + pointName + "' in '" +
                                           pluginDescriptor + "' plugin", pluginDescriptor.getPluginId());
      }

      if (beanClassName != null && interfaceClassName != null) {
        throw componentManager.createError("Both 'beanClass' and 'interface' attributes are specified for extension point '" + pointName + "' in '" +
                                           pluginDescriptor + "' plugin", pluginDescriptor.getPluginId());
      }

      boolean dynamic = Boolean.parseBoolean(element.getAttributeValue("dynamic"));
      String className = interfaceClassName == null ? beanClassName : interfaceClassName;
      doRegisterExtensionPoint(pointName, className, pluginDescriptor, interfaceClassName != null, dynamic);
    }
  }

  public boolean unregisterExtensions(@NotNull String extensionPointName,
                                      @NotNull PluginDescriptor pluginDescriptor,
                                      @NotNull List<ExtensionDescriptor> elements,
                                      @NotNull List<Runnable> priorityListenerCallbacks,
                                      @NotNull List<Runnable> listenerCallbacks) {
    ExtensionPointImpl<?> point = getExtensionPointIfRegistered(extensionPointName);
    if (point == null) {
      return false;
    }

    point.unregisterExtensions(componentManager, pluginDescriptor, elements, priorityListenerCallbacks, listenerCallbacks);
    return true;
  }

  public void resetExtensionPoints(@NotNull List<ExtensionPointDescriptor> descriptors, @NotNull PluginDescriptor pluginDescriptor) {
    for (ExtensionPointDescriptor descriptor : descriptors) {
      ExtensionPointImpl<?> extensionPoint = getExtensionPointIfRegistered(descriptor.getQualifiedName(pluginDescriptor));
      if (extensionPoint != null) {
        extensionPoint.reset();
      }
    }
  }

  public void clearUserCache() {
    extensionPoints.values().forEach(ExtensionPointImpl::clearUserCache);
  }

  /**
   * You must call {@link #resetExtensionPoints} before otherwise event `ExtensionEvent.REMOVED` will be not fired.
   */
  public void unregisterExtensionPoints(@NotNull List<ExtensionPointDescriptor> descriptors,
                                        @NotNull PluginDescriptor pluginDescriptor) {
    if (descriptors.isEmpty()) {
      return;
    }

    Map<String, ExtensionPointImpl<?>> map = new HashMap<>(extensionPoints);
    for (ExtensionPointDescriptor descriptor : descriptors) {
      map.remove(descriptor.getQualifiedName(pluginDescriptor));
    }
    // Map.copyOf is not available in extension module
    extensionPoints = map;
  }

  @TestOnly
  public void registerExtensionPoint(@NotNull BaseExtensionPointName<?> extensionPoint,
                                     @NotNull String extensionPointBeanClass,
                                     @NotNull ExtensionPoint.Kind kind,
                                     @NotNull Disposable parentDisposable) {
    String extensionPointName = extensionPoint.getName();
    registerExtensionPoint(extensionPointName, extensionPointBeanClass, kind, false);
    Disposer.register(parentDisposable, () -> unregisterExtensionPoint(extensionPointName));
  }

  @TestOnly
  @Override
  public void registerExtensionPoint(@NotNull String extensionPointName,
                                     @NotNull String extensionPointBeanClass,
                                     @NotNull ExtensionPoint.Kind kind,
                                     boolean dynamic) {
    PluginDescriptor pluginDescriptor = new DefaultPluginDescriptor(PluginId.getId("fakeIdForTests"));
    doRegisterExtensionPoint(extensionPointName, extensionPointBeanClass, pluginDescriptor, kind == ExtensionPoint.Kind.INTERFACE, dynamic);
  }

  @TestOnly
  public <T> @NotNull ExtensionPointImpl<T> registerPoint(@NotNull String name,
                                                          @NotNull Class<T> extensionClass,
                                                          @NotNull PluginDescriptor pluginDescriptor,
                                                          boolean isDynamic) {
    return doRegisterExtensionPoint(name,
                                    extensionClass.getName(),
                                    pluginDescriptor,
                                    extensionClass.isInterface() || (extensionClass.getModifiers() & Modifier.ABSTRACT) != 0,
                                    isDynamic);
  }

  @TestOnly
  private @NotNull <T> ExtensionPointImpl<T> doRegisterExtensionPoint(@NotNull String name,
                                                                      @NotNull String extensionClass,
                                                                      @NotNull PluginDescriptor pluginDescriptor,
                                                                      boolean isInterface,
                                                                      boolean dynamic) {
    ExtensionPointImpl<T> point;
    if (isInterface) {
      point = new InterfaceExtensionPoint<>(name, extensionClass, pluginDescriptor, componentManager, null, dynamic);
    }
    else {
      point = new BeanExtensionPoint<>(name, extensionClass, pluginDescriptor, componentManager, dynamic);
    }
    checkThatPointNotDuplicated(name, point.getPluginDescriptor());

    Map<String, ExtensionPointImpl<?>> newMap = new HashMap<>(extensionPoints.size() + 1);
    newMap.putAll(extensionPoints);
    newMap.put(name, point);
    extensionPoints = Collections.unmodifiableMap(newMap);
    if (DEBUG_REGISTRATION) {
      epTraces.put(name, new Throwable("Original registration for " + name));
    }
    return point;
  }

  /**
   * To register extensions for {@link com.intellij.openapi.util.KeyedExtensionCollector} for test purposes,
   * where extension instance can be KeyedLazyInstance and not a real bean class,
   * because often it is not possible to use one (for example, {@link com.intellij.lang.LanguageExtensionPoint}).
   */
  @TestOnly
  public <T> @NotNull ExtensionPointImpl<T> registerFakeBeanPoint(@NotNull String name, @NotNull PluginDescriptor pluginDescriptor) {
    // any object name can be used, because EP must not create any instance
    return doRegisterExtensionPoint(name, Object.class.getName(), pluginDescriptor, false, false);
  }

  private void checkThatPointNotDuplicated(@NotNull String pointName, @NotNull PluginDescriptor pluginDescriptor) {
    if (!hasExtensionPoint(pointName)) {
      return;
    }

    PluginId id1 = getExtensionPoint(pointName).getPluginDescriptor().getPluginId();
    PluginId id2 = pluginDescriptor.getPluginId();
    @NonNls String message = "Duplicate registration for EP '" + pointName + "': first in " + id1 + ", second in " + id2;
    if (DEBUG_REGISTRATION) {
      LOG.error(message, epTraces.get(pointName));
    }
    throw componentManager.createError(message, pluginDescriptor.getPluginId());
  }

  // _only_ for CoreApplicationEnvironment
  public void registerExtensionPoints(@NotNull List<ExtensionPointDescriptor> points, @NotNull PluginDescriptor pluginDescriptor) {
    Map<String, ExtensionPointImpl<?>> map = new HashMap<>(extensionPoints);
    createExtensionPoints(points, componentManager, map, pluginDescriptor);
    extensionPoints = map;
  }

  public void setPoints(@NotNull Map<String, ExtensionPointImpl<?>> value) {
    extensionPoints = value;
  }

  @ApiStatus.Internal
  public static void createExtensionPoints(@NotNull List<ExtensionPointDescriptor> points,
                                           @NotNull ComponentManager componentManager,
                                           @NotNull Map<? super String, ExtensionPointImpl<?>> result,
                                           @NotNull PluginDescriptor pluginDescriptor) {
    for (ExtensionPointDescriptor descriptor : points) {
      String name = descriptor.getQualifiedName(pluginDescriptor);
      ExtensionPointImpl<?> point;
      if (descriptor.isBean) {
        point = new BeanExtensionPoint<>(name, descriptor.className, pluginDescriptor, componentManager, descriptor.isDynamic);
      }
      else {
        point = new InterfaceExtensionPoint<>(name, descriptor.className, pluginDescriptor, componentManager, null, descriptor.isDynamic);
      }

      ExtensionPointImpl<?> old = result.putIfAbsent(name, point);
      if (old != null) {
        PluginDescriptor oldPluginDescriptor = old.getPluginDescriptor();
        throw componentManager.createError("Duplicate registration for EP " + name + ": " +
                                           "first in " + oldPluginDescriptor + ", second in " + pluginDescriptor,
                                           pluginDescriptor.getPluginId());
      }
    }
  }

  @Override
  public @NotNull <T> ExtensionPointImpl<T> getExtensionPoint(@NotNull String extensionPointName) {
    ExtensionPointImpl<T> extensionPoint = getExtensionPointIfRegistered(extensionPointName);
    if (extensionPoint == null) {
      throw new IllegalArgumentException("Missing extension point: " + extensionPointName + " in container " + componentManager);
    }
    return extensionPoint;
  }

  @Override
  public <T> ExtensionPointImpl<T> getExtensionPointIfRegistered(@NotNull String extensionPointName) {
    //noinspection unchecked
    return (ExtensionPointImpl<T>)extensionPoints.get(extensionPointName);
  }

  @Override
  public @NotNull <T> ExtensionPoint<T> getExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName) {
    return getExtensionPoint(extensionPointName.getName());
  }

  @TestOnly
  public void processExtensionPoints(@NotNull Consumer<? super ExtensionPointImpl<?>> consumer) {
    extensionPoints.values().forEach(consumer);
  }

  @ApiStatus.Internal
  public @Nullable <T> T findExtensionByClass(@NotNull Class<T> aClass) {
    // TeamCity plugin wants DefaultDebugExecutor in constructor
    if (aClass.getName().equals("com.intellij.execution.executors.DefaultDebugExecutor")) {
      return getExtensionPointIfRegistered("com.intellij.executor").findExtension(aClass, false, ThreeState.YES);
    }

    for (ExtensionPointImpl<?> point : extensionPoints.values()) {
      if (!(point instanceof InterfaceExtensionPoint)) {
        continue;
      }

      try {
        Class<?> extensionClass = point.getExtensionClass();
        if (!extensionClass.isAssignableFrom(aClass)) {
          continue;
        }

        //noinspection unchecked
        T extension = ((ExtensionPointImpl<T>)point).findExtension(aClass, false, ThreeState.YES);
        if (extension != null) {
          return extension;
        }
      }
      catch (Throwable e) {
        LOG.warn("error during findExtensionPointByClass", e);
      }
    }
    return null;
  }

  @Override
  @TestOnly
  public void unregisterExtensionPoint(@NotNull String extensionPointName) {
    ExtensionPointImpl<?> extensionPoint = getExtensionPointIfRegistered(extensionPointName);
    if (extensionPoint != null) {
      extensionPoint.reset();

      Map<String, ExtensionPointImpl<?>> map = new HashMap<>(extensionPoints);
      map.remove(extensionPointName);
      extensionPoints = Collections.unmodifiableMap(map);
    }
  }

  @Override
  public boolean hasExtensionPoint(@NotNull String extensionPointName) {
    return extensionPoints.containsKey(extensionPointName);
  }

  @Override
  public boolean hasExtensionPoint(@NotNull ExtensionPointName<?> extensionPointName) {
    return hasExtensionPoint(extensionPointName.getName());
  }

  @Override
  public String toString() {
    return componentManager.toString();
  }
}