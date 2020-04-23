// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ThreeState;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jetbrains.annotations.*;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@ApiStatus.Internal
public final class ExtensionsAreaImpl implements ExtensionsArea {
  private static final Logger LOG = Logger.getInstance(ExtensionsAreaImpl.class);

  private static final boolean DEBUG_REGISTRATION = false;

  private final ComponentManager myComponentManager;
  private final Map<String, ExtensionPointImpl<?>> myExtensionPoints = new ConcurrentHashMap<>();
  private final Map<String,Throwable> myEPTraces = DEBUG_REGISTRATION ? new THashMap<>() : null;

  public ExtensionsAreaImpl(@NotNull ComponentManager componentManager) {
    myComponentManager = componentManager;
  }

  @TestOnly
  public final void notifyAreaReplaced(@Nullable ExtensionsAreaImpl newArea) {
    Set<String> processedEPs = new THashSet<>();
    for (ExtensionPointImpl<?> point : myExtensionPoints.values()) {
      point.notifyAreaReplaced(this);
      processedEPs.add(point.getName());
    }

    if (newArea == null) {
      return;
    }

    for (ExtensionPointImpl<?> point : newArea.myExtensionPoints.values()) {
      if (!processedEPs.contains(point.getName())) {
        point.notifyAreaReplaced(this);
      }
    }
  }

  @TestOnly
  public void registerExtensionPoints(@NotNull PluginDescriptor pluginDescriptor, @NotNull List<? extends Element> extensionPointElements) {
    for (Element element : extensionPointElements) {
      registerExtensionPoint(pluginDescriptor, element);
    }
  }

  private void registerExtensionPoint(@NotNull PluginDescriptor pluginDescriptor, @NotNull Element extensionPointElement) {
    String pointName = getExtensionPointName(extensionPointElement, pluginDescriptor);

    String beanClassName = extensionPointElement.getAttributeValue("beanClass");
    String interfaceClassName = extensionPointElement.getAttributeValue("interface");
    if (beanClassName == null && interfaceClassName == null) {
      throw myComponentManager.createError("Neither 'beanClass' nor 'interface' attribute is specified for extension point '" + pointName + "' in '" + pluginDescriptor + "' plugin", pluginDescriptor.getPluginId());
    }

    if (beanClassName != null && interfaceClassName != null) {
      throw myComponentManager.createError("Both 'beanClass' and 'interface' attributes are specified for extension point '" + pointName + "' in '" + pluginDescriptor + "' plugin", pluginDescriptor.getPluginId());
    }

    boolean dynamic = Boolean.parseBoolean(extensionPointElement.getAttributeValue("dynamic"));
    String className = interfaceClassName == null ? beanClassName : interfaceClassName;
    doRegisterExtensionPoint(pointName, className, pluginDescriptor, interfaceClassName != null, dynamic);
  }

  private @NotNull String getExtensionPointName(@NotNull Element extensionPointElement, @NotNull PluginDescriptor pluginDescriptor) {
    String pointName = extensionPointElement.getAttributeValue("qualifiedName");
    if (pointName == null) {
      final String name = extensionPointElement.getAttributeValue("name");
      if (name == null) {
        throw myComponentManager.createError("'name' attribute not specified for extension point in '" + pluginDescriptor + "' plugin", pluginDescriptor.getPluginId());
      }

      assert pluginDescriptor.getPluginId() != null;
      pointName = pluginDescriptor.getPluginId().getIdString() + '.' + name;
    }
    return pointName;
  }

  @Override
  public void registerExtension(final @NotNull PluginDescriptor pluginDescriptor, final @NotNull Element extensionElement, String extensionNs) {
    String epName = extractPointName(extensionElement, extensionNs);
    registerExtension(getExtensionPoint(epName), pluginDescriptor, extensionElement);
  }

  @Override
  public void registerExtension(@NotNull ExtensionPoint<?> extensionPoint, @NotNull PluginDescriptor pluginDescriptor, @NotNull Element extensionElement) {
    ((ExtensionPointImpl<?>)extensionPoint).createAndRegisterAdapter(extensionElement, pluginDescriptor, myComponentManager);
  }

  public boolean unregisterExtensions(@NotNull String extensionPointName, @NotNull PluginDescriptor loadedPluginDescriptor, @NotNull List<Element> elements, @NotNull List<Runnable> listenerCallbacks) {
    ExtensionPointImpl<?> point = myExtensionPoints.get(extensionPointName);
    if (point == null) {
      return false;
    }

    point.unregisterExtensions(myComponentManager, loadedPluginDescriptor, elements, listenerCallbacks);
    return true;
  }

  // extensionPoints here are raw and not initialized (not the same instance, only name can be used)
  public void resetExtensionPoints(@NotNull List<ExtensionPointImpl<?>> extensionPoints) {
    for (ExtensionPointImpl<?> point : extensionPoints) {
      ExtensionPointImpl<?> extensionPoint = myExtensionPoints.get(point.getName());
      if (extensionPoint != null) {
        extensionPoint.reset();
      }
    }
  }

  public void clearUserCache() {
    myExtensionPoints.values().forEach(ExtensionPointImpl::clearUserCache);
  }

  // note about extension point here the same as for resetExtensionPoints
  /**
   * You must call {@link #resetExtensionPoints} before otherwise event ExtensionEvent.REMOVED will be not fired.
   */
  public void unregisterExtensionPoints(@NotNull List<ExtensionPointImpl<?>> extensionPoints) {
    for (ExtensionPointImpl<?> point : extensionPoints) {
      myExtensionPoints.remove(point.getName());
    }
  }

  public static @NotNull String extractPointName(@NotNull Element extensionElement, @Nullable String ns) {
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

  @Override
  @TestOnly
  public void registerExtensionPoint(@NotNull @NonNls String extensionPointName,
                                     @NotNull String extensionPointBeanClass,
                                     @NotNull ExtensionPoint.Kind kind) {
    doRegisterExtensionPoint(extensionPointName, extensionPointBeanClass, kind);
  }

  @TestOnly
  public void registerExtensionPoint(@NotNull BaseExtensionPointName<?> extensionPoint,
                                     @NotNull String extensionPointBeanClass,
                                     @NotNull ExtensionPoint.Kind kind,
                                     @NotNull Disposable parentDisposable) {
    String extensionPointName = extensionPoint.getName();
    doRegisterExtensionPoint(extensionPointName, extensionPointBeanClass, kind);
    Disposer.register(parentDisposable, () -> unregisterExtensionPoint(extensionPointName));
  }

  @TestOnly
  void doRegisterExtensionPoint(@NotNull String extensionPointName, @NotNull String extensionPointBeanClass, @NotNull ExtensionPoint.Kind kind) {
    IdeaPluginDescriptor pluginDescriptor = new DefaultPluginDescriptor(PluginId.getId("FakeIdForTests"));
    doRegisterExtensionPoint(extensionPointName, extensionPointBeanClass, pluginDescriptor, kind == ExtensionPoint.Kind.INTERFACE, false);
  }

  @TestOnly
  public <T> ExtensionPointImpl<T> registerPoint(@NotNull String name,
                                                 @NotNull Class<T> extensionClass,
                                                 @NotNull PluginDescriptor pluginDescriptor) {
    return doRegisterExtensionPoint(name, extensionClass.getName(), pluginDescriptor, extensionClass.isInterface() || (extensionClass.getModifiers() & Modifier.ABSTRACT) != 0,
                                    false);
  }

  private @NotNull <T> ExtensionPointImpl<T> doRegisterExtensionPoint(@NotNull String name, @NotNull String extensionClass,
                                                                      @NotNull PluginDescriptor pluginDescriptor, boolean isInterface, boolean dynamic) {
    ExtensionPointImpl<T> point;
    if (isInterface) {
      point = new InterfaceExtensionPoint<>(name, extensionClass, pluginDescriptor, dynamic);
    }
    else {
      point = new BeanExtensionPoint<>(name, extensionClass, pluginDescriptor, dynamic);
    }
    point.setComponentManager(myComponentManager);
    registerExtensionPoint(point);
    return point;
  }

  /**
   * To register extensions for {@link com.intellij.openapi.util.KeyedExtensionCollector} for test purposes, where extension instance can be KeyedLazyInstance and not a real bean class,
   * because often it is not possible to use one (for example, {@link com.intellij.lang.LanguageExtensionPoint}).
   */
  @TestOnly
  public <T> ExtensionPointImpl<T> registerFakeBeanPoint(@NotNull String name, @NotNull PluginDescriptor pluginDescriptor) {
    // any object name can be used, because EP must not create any instance
    return doRegisterExtensionPoint(name, Object.class.getName(), pluginDescriptor, false, false);
  }

  private void checkThatPointNotDuplicated(@NotNull String pointName, @NotNull PluginDescriptor pluginDescriptor) {
    if (!hasExtensionPoint(pointName)) {
      return;
    }

    PluginId id1 = getExtensionPoint(pointName).getPluginDescriptor().getPluginId();
    PluginId id2 = pluginDescriptor.getPluginId();
    String message = "Duplicate registration for EP '" + pointName + "': first in " + id1 + ", second in " + id2;
    if (DEBUG_REGISTRATION) {
      LOG.error(message, myEPTraces.get(pointName));
    }
    throw myComponentManager.createError(message, pluginDescriptor.getPluginId());
  }

  private void registerExtensionPoint(@NotNull ExtensionPointImpl<?> point) {
    String name = point.getName();
    checkThatPointNotDuplicated(name, point.getPluginDescriptor());
    myExtensionPoints.put(name, point);
    if (DEBUG_REGISTRATION) {
      myEPTraces.put(name, new Throwable("Original registration for " + name));
    }
  }

  @ApiStatus.Internal
  public void registerExtensionPoints(@NotNull List<? extends ExtensionPointImpl<?>> points, boolean clonePoint) {
    ComponentManager componentManager = myComponentManager;
    Map<String, ExtensionPointImpl<?>> map = myExtensionPoints;
    for (ExtensionPointImpl<?> point : points) {
      if (clonePoint) {
        point = point.cloneFor(componentManager);
      }
      else {
        point.setComponentManager(componentManager);
      }

      ExtensionPointImpl<?> old = map.put(point.getName(), point);
      if (old != null) {
        map.put(point.getName(), old);
        throw componentManager.createError("Duplicate registration for EP '" + point.getName() + "': first in " + old.getPluginDescriptor() +
                                             ", second in " + point.getPluginDescriptor(), point.getPluginDescriptor().getPluginId());
      }
    }
  }

  @Override
  public @NotNull <T> ExtensionPointImpl<T> getExtensionPoint(@NotNull String extensionPointName) {
    @SuppressWarnings("unchecked")
    ExtensionPointImpl<T> extensionPoint = (ExtensionPointImpl<T>)myExtensionPoints.get(extensionPointName);
    if (extensionPoint == null) {
      throw new IllegalArgumentException("Missing extension point: " + extensionPointName + " in container " + myComponentManager);
    }
    return extensionPoint;
  }

  @ApiStatus.Internal
  public boolean registerExtensions(@NotNull String pointName,
                                    @NotNull List<Element> extensions,
                                    @NotNull IdeaPluginDescriptor pluginDescriptor,
                                    @NotNull ComponentManager componentManager,
                                    @Nullable List<Runnable> listenerCallbacks)  {
    ExtensionPointImpl<?> point = myExtensionPoints.get(pointName);
    if (point == null) {
      return false;
    }

    point.registerExtensions(extensions, pluginDescriptor, componentManager, listenerCallbacks);
    return true;
  }

  @Override
  public @Nullable <T> ExtensionPointImpl<T> getExtensionPointIfRegistered(@NotNull String extensionPointName) {
    //noinspection unchecked
    return (ExtensionPointImpl<T>)myExtensionPoints.get(extensionPointName);
  }

  @Override
  public @NotNull <T> ExtensionPoint<T> getExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName) {
    return getExtensionPoint(extensionPointName.getName());
  }

  @TestOnly
  public void processExtensionPoints(@NotNull Consumer<ExtensionPointImpl<?>> consumer) {
    myExtensionPoints.values().forEach(consumer);
  }

  @Override
  public @NotNull List<ExtensionPoint<?>> getExtensionPoints() {
    return Collections.unmodifiableList(new ArrayList<>(myExtensionPoints.values()));
  }

  @ApiStatus.Internal
  public @Nullable <T> T findExtensionByClass(@NotNull Class<T> aClass) {
    // TeamCity plugin wants DefaultDebugExecutor in constructor
    if (aClass.getName().equals("com.intellij.execution.executors.DefaultDebugExecutor")) {
      //noinspection unchecked
      return ((ExtensionPointImpl<T>)myExtensionPoints.get("com.intellij.executor")).findExtension(aClass, false, ThreeState.YES);
    }

    for (ExtensionPointImpl<?> point : myExtensionPoints.values()) {
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
  public void unregisterExtensionPoint(@NotNull String extensionPointName) {
    ExtensionPointImpl<?> extensionPoint = myExtensionPoints.get(extensionPointName);
    if (extensionPoint != null) {
      extensionPoint.reset();
      myExtensionPoints.remove(extensionPointName);
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
    return myComponentManager.toString();
  }
}