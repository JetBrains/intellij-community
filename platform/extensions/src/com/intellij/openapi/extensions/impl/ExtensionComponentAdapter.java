// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

/**
 * @author Alexander Kireyev
 */
public abstract class ExtensionComponentAdapter implements LoadingOrder.Orderable {
  public static final ExtensionComponentAdapter[] EMPTY_ARRAY = new ExtensionComponentAdapter[0];

  private final PluginDescriptor myPluginDescriptor;
  @NotNull
  private Object myImplementationClassOrName; // Class or String

  private final String myOrderId;
  private final LoadingOrder myOrder;

  public ExtensionComponentAdapter(@NotNull String implementationClassName,
                                   @Nullable PluginDescriptor pluginDescriptor,
                                   @Nullable String orderId,
                                   @NotNull LoadingOrder order) {
    myImplementationClassOrName = implementationClassName;
    myPluginDescriptor = pluginDescriptor;

    myOrderId = orderId;
    myOrder = order;
  }

  abstract boolean isInstanceCreated();

  @NotNull
  public Object createInstance(@Nullable PicoContainer container) {
    Object instance;
    try {
      Class<?> impl = getImplementationClass();

      ExtensionPointImpl.CHECK_CANCELED.run();

      instance = instantiateClass(impl, container);
      initInstance(instance);
    }
    catch (ProcessCanceledException | ExtensionNotApplicableException e) {
      throw e;
    }
    catch (Throwable t) {
      PluginId pluginId = myPluginDescriptor != null ? myPluginDescriptor.getPluginId() : null;
      throw new PicoPluginExtensionInitializationException(t.getMessage(), t, pluginId);
    }

    if (instance instanceof PluginAware) {
      ((PluginAware)instance).setPluginDescriptor(myPluginDescriptor);
    }
    return instance;
  }

  @NotNull
  protected Object instantiateClass(@NotNull Class<?> clazz, @Nullable PicoContainer container) {
    return ReflectionUtil.newInstance(clazz);
  }

  protected void initInstance(@NotNull Object instance) {
  }

  @Override
  public final LoadingOrder getOrder() {
    return myOrder;
  }

  @Override
  public final String getOrderId() {
    return myOrderId;
  }

  @Nullable
  public final PluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
  }

  @NotNull
  public final Class<?> getImplementationClass() {
    Object implementationClassOrName = myImplementationClassOrName;
    if (implementationClassOrName instanceof String) {
      try {
        ClassLoader classLoader = myPluginDescriptor == null ? getClass().getClassLoader() : myPluginDescriptor.getPluginClassLoader();
        if (classLoader == null) {
          classLoader = getClass().getClassLoader();
        }
        myImplementationClassOrName = implementationClassOrName = Class.forName((String)implementationClassOrName, false, classLoader);
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
    return (Class<?>)implementationClassOrName;
  }

  @NotNull
  public final String getAssignableToClassName() {
    Object implementationClassOrName = myImplementationClassOrName;
    if (implementationClassOrName instanceof String) {
      return (String)implementationClassOrName;
    }
    return ((Class)implementationClassOrName).getName();
  }

  @Override
  public String toString() {
    return "ExtensionComponentAdapter[" + getAssignableToClassName() + "]: plugin=" + myPluginDescriptor;
  }
}
