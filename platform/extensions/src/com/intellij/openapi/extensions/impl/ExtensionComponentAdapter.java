// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ExtensionComponentAdapter implements LoadingOrder.Orderable {
  public static final ExtensionComponentAdapter[] EMPTY_ARRAY = new ExtensionComponentAdapter[0];

  @NotNull
  private final PluginDescriptor myPluginDescriptor;
  @NotNull
  Object myImplementationClassOrName; // Class or String

  private final String myOrderId;
  private final LoadingOrder myOrder;

  ExtensionComponentAdapter(@NotNull String implementationClassName,
                            @NotNull PluginDescriptor pluginDescriptor,
                            @Nullable String orderId,
                            @NotNull LoadingOrder order) {
    myImplementationClassOrName = implementationClassName;
    myPluginDescriptor = pluginDescriptor;

    myOrderId = orderId;
    myOrder = order;
  }

  abstract boolean isInstanceCreated();

  @NotNull
  public <T> T createInstance(@NotNull ComponentManager componentManager) {
    Class<T> aClass;
    try {
      aClass = getImplementationClass();
    }
    catch (ClassNotFoundException e) {
      throw componentManager.createError(e, myPluginDescriptor.getPluginId());
    }

    T instance = instantiateClass(aClass, componentManager);
    if (instance instanceof PluginAware) {
      ((PluginAware)instance).setPluginDescriptor(myPluginDescriptor);
    }
    return instance;
  }

  @NotNull
  protected <T> T instantiateClass(@NotNull Class<T> aClass, @NotNull ComponentManager componentManager) {
    return componentManager.instantiateClass(aClass, myPluginDescriptor.getPluginId());
  }

  @Override
  public final LoadingOrder getOrder() {
    return myOrder;
  }

  @Override
  public final String getOrderId() {
    return myOrderId;
  }

  @NotNull
  public final PluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
  }

  @NotNull
  public final <T> Class<T> getImplementationClass() throws ClassNotFoundException {
    Object implementationClassOrName = myImplementationClassOrName;
    if (implementationClassOrName instanceof String) {
      ClassLoader classLoader = myPluginDescriptor.getPluginClassLoader();
      if (classLoader == null) {
        classLoader = getClass().getClassLoader();
      }
      myImplementationClassOrName = implementationClassOrName = Class.forName((String)implementationClassOrName, false, classLoader);
    }
    //noinspection unchecked
    return (Class<T>)implementationClassOrName;
  }

  @NotNull
  public final String getAssignableToClassName() {
    Object implementationClassOrName = myImplementationClassOrName;
    if (implementationClassOrName instanceof String) {
      return (String)implementationClassOrName;
    }
    return ((Class<?>)implementationClassOrName).getName();
  }

  @Override
  public String toString() {
    return "ExtensionComponentAdapter(impl=" + getAssignableToClassName() + ", plugin=" + myPluginDescriptor + ")";
  }
}
