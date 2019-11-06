// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jetbrains.annotations.*;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ExtensionsAreaImpl implements ExtensionsArea {
  private static final Logger LOG = Logger.getInstance(ExtensionsAreaImpl.class);
  public static final String ATTRIBUTE_AREA = "area";

  private static final boolean DEBUG_REGISTRATION = Boolean.FALSE.booleanValue(); // not compile-time constant to avoid yellow code

  private final ComponentManager myComponentManager;
  private final Map<String, ExtensionPointImpl<?>> myExtensionPoints = ContainerUtil.newConcurrentMap();
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

  public void registerExtensionPoints(@NotNull PluginDescriptor pluginDescriptor,
                                      @NotNull List<Element> extensionPointElements,
                                      @NotNull ComponentManager componentManager) {
    for (Element element : extensionPointElements) {
      registerExtensionPoint(pluginDescriptor, element, componentManager);
    }
  }

  private void registerExtensionPoint(@NotNull PluginDescriptor pluginDescriptor,
                                      @NotNull Element extensionPointElement,
                                      @NotNull ComponentManager componentManager) {
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

    ExtensionPointImpl<Object> point;
    if (interfaceClassName == null) {
      point = new BeanExtensionPoint<>(pointName, beanClassName, componentManager, pluginDescriptor, dynamic);
    }
    else {
      point = new InterfaceExtensionPoint<>(pointName, interfaceClassName, componentManager, pluginDescriptor, dynamic);
    }
    registerExtensionPoint(point);
  }

  @NotNull
  public String getExtensionPointName(@NotNull Element extensionPointElement, @NotNull PluginDescriptor pluginDescriptor) {
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
  public void registerExtension(@NotNull final PluginDescriptor pluginDescriptor, @NotNull final Element extensionElement, String extensionNs) {
    String epName = extractPointName(extensionElement, extensionNs);
    registerExtension(getExtensionPoint(epName), pluginDescriptor, extensionElement);
  }

  @Override
  public void registerExtension(@NotNull ExtensionPoint<?> extensionPoint, @NotNull PluginDescriptor pluginDescriptor, @NotNull Element extensionElement) {
    ((ExtensionPointImpl<?>)extensionPoint).createAndRegisterAdapter(extensionElement, pluginDescriptor, myComponentManager);
  }

  // don't want to expose clearCache directly
  static void extensionsRegistered(@NotNull ExtensionPointImpl<?>[] points) {
    for (ExtensionPointImpl<?> point : points) {
      point.clearCache();
    }
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

  void doRegisterExtensionPoint(@NotNull String extensionPointName, @NotNull String extensionPointBeanClass, @NotNull ExtensionPoint.Kind kind) {
    IdeaPluginDescriptor pluginDescriptor = new UndefinedPluginDescriptor();
    ExtensionPointImpl<Object> point;
    if (kind == ExtensionPoint.Kind.INTERFACE) {
      point = new InterfaceExtensionPoint<>(extensionPointName, extensionPointBeanClass, myComponentManager, pluginDescriptor, false);
    }
    else {
      point = new BeanExtensionPoint<>(extensionPointName, extensionPointBeanClass, myComponentManager, pluginDescriptor, false);
    }
    registerExtensionPoint(point);
  }

  @TestOnly
  public <T> ExtensionPointImpl<T> registerPoint(@NotNull String name,
                                                 @NotNull Class<T> extensionClass,
                                                 @NotNull PluginDescriptor pluginDescriptor) {
    ExtensionPointImpl<T> point;
    if (extensionClass.isInterface() || (extensionClass.getModifiers() & Modifier.ABSTRACT) != 0) {
      point = new InterfaceExtensionPoint<>(name, extensionClass.getName(), myComponentManager, pluginDescriptor, false);
    }
    else {
      point = new BeanExtensionPoint<>(name, extensionClass.getName(), myComponentManager, pluginDescriptor, false);
    }
    registerExtensionPoint(point);
    return point;
  }

  /**
   * To register extensions for {@link com.intellij.openapi.util.KeyedExtensionCollector} for test purposes, where extension instance can be KeyedLazyInstance and not a real bean class,
   * because often it is not possible to use one (for example, {@link com.intellij.lang.LanguageExtensionPoint}).
   */
  @SuppressWarnings("UnusedReturnValue")
  @TestOnly
  public <T> ExtensionPointImpl<T> registerFakeBeanPoint(@NotNull String name, @NotNull PluginDescriptor pluginDescriptor) {
    // any object name can be used, because EP must not create any instance
    ExtensionPointImpl<T> point = new BeanExtensionPoint<>(name, Object.class.getName(), myComponentManager, pluginDescriptor, false);
    registerExtensionPoint(point);
    return point;
  }

  @Nullable
  private static PluginId id(@NotNull PluginDescriptor descriptor) {
    return descriptor instanceof UndefinedPluginDescriptor ? null : descriptor.getPluginId();
  }

  private void checkThatPointNotDuplicated(@NotNull String pointName, @NotNull PluginDescriptor pluginDescriptor) {
    if (!hasExtensionPoint(pointName)) {
      return;
    }

    PluginId id1 = id(getExtensionPoint(pointName).getDescriptor());
    PluginId id2 = id(pluginDescriptor);
    String message = "Duplicate registration for EP '" + pointName + "': first in " + id1 + ", second in " + id2;
    if (DEBUG_REGISTRATION) {
      LOG.error(message, myEPTraces.get(pointName));
    }
    throw myComponentManager.createError(message, pluginDescriptor.getPluginId());
  }

  private void registerExtensionPoint(@NotNull ExtensionPointImpl<?> point) {
    String name = point.getName();
    checkThatPointNotDuplicated(name, point.getDescriptor());
    myExtensionPoints.put(name, point);
    if (DEBUG_REGISTRATION) {
      myEPTraces.put(name, new Throwable("Original registration for " + name));
    }
  }

  @NotNull
  @Override
  public <T> ExtensionPointImpl<T> getExtensionPoint(@NotNull String extensionPointName) {
    @SuppressWarnings("unchecked")
    ExtensionPointImpl<T> extensionPoint = (ExtensionPointImpl<T>)myExtensionPoints.get(extensionPointName);
    if (extensionPoint == null) {
      throw new IllegalArgumentException("Missing extension point: " + extensionPointName + " in container " + myComponentManager);
    }
    return extensionPoint;
  }

  @ApiStatus.Internal
  public boolean registerExtensions(@NotNull String pointName,
                                    @NotNull List<? extends Element> extensions,
                                    @NotNull IdeaPluginDescriptor pluginDescriptor,
                                    @NotNull ComponentManager componentManager,
                                    boolean notifyListeners)  {
    ExtensionPointImpl<?> point = myExtensionPoints.get(pointName);
    if (point == null) {
      return false;
    }

    if (point.myComponentManager != componentManager) {
      LOG.error("The same point on different levels (pointName=" + pointName +  ")");
    }
    point.registerExtensions(extensions, pluginDescriptor, componentManager, notifyListeners);
    return true;
  }

  @Nullable
  @Override
  public <T> ExtensionPoint<T> getExtensionPointIfRegistered(@NotNull String extensionPointName) {
    @SuppressWarnings("unchecked")
    ExtensionPointImpl<T> extensionPoint = (ExtensionPointImpl<T>)myExtensionPoints.get(extensionPointName);
    return extensionPoint;
  }

  @NotNull
  @Override
  public <T> ExtensionPoint<T> getExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName) {
    return getExtensionPoint(extensionPointName.getName());
  }

  @NotNull
  @Override
  public ExtensionPointImpl<?>[] getExtensionPoints() {
    return myExtensionPoints.values().toArray(new ExtensionPointImpl[0]);
  }

  @Nullable
  @ApiStatus.Internal
  public <T> T findExtensionByClass(@NotNull Class<T> aClass) {
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
  public void unregisterExtensionPoint(@NotNull final String extensionPointName) {
    ExtensionPoint<?> extensionPoint = myExtensionPoints.get(extensionPointName);
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