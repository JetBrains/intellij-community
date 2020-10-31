// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ExtensionComponentAdapter implements LoadingOrder.Orderable {
  public static final ExtensionComponentAdapter[] EMPTY_ARRAY = new ExtensionComponentAdapter[0];

  private final @NotNull PluginDescriptor pluginDescriptor;

  // Class or String
  @NotNull Object implementationClassOrName;
  private final ImplementationClassResolver implementationClassResolver;

  private final String orderId;
  private final LoadingOrder order;

  ExtensionComponentAdapter(@NotNull String implementationClassName,
                            @NotNull PluginDescriptor pluginDescriptor,
                            @Nullable String orderId,
                            @NotNull LoadingOrder order,
                            @NotNull ImplementationClassResolver implementationClassResolver) {
    implementationClassOrName = implementationClassName;
    this.pluginDescriptor = pluginDescriptor;

    this.orderId = orderId;
    this.order = order;

    this.implementationClassResolver = implementationClassResolver;
  }

  abstract boolean isInstanceCreated();

  public @NotNull <T> T createInstance(@NotNull ComponentManager componentManager) {
    Class<T> aClass;
    try {
      aClass = getImplementationClass(componentManager);
    }
    catch (ClassNotFoundException e) {
      throw componentManager.createError(e, pluginDescriptor.getPluginId());
    }

    T instance = instantiateClass(aClass, componentManager);
    if (instance instanceof PluginAware) {
      ((PluginAware)instance).setPluginDescriptor(pluginDescriptor);
    }
    return instance;
  }

  protected @NotNull <T> T instantiateClass(@NotNull Class<T> aClass, @NotNull ComponentManager componentManager) {
    return componentManager.instantiateClass(aClass, pluginDescriptor.getPluginId());
  }

  @Override
  public final LoadingOrder getOrder() {
    return order;
  }

  @Override
  public final String getOrderId() {
    return orderId;
  }

  public final @NotNull PluginDescriptor getPluginDescriptor() {
    return pluginDescriptor;
  }

  public final @NotNull <T> Class<T> getImplementationClass(@NotNull ComponentManager componentManager) throws ClassNotFoundException {
    //noinspection unchecked
    return (Class<T>)implementationClassResolver.resolveImplementationClass(componentManager, this);
  }

  // used externally - cannot be package-local
  public final @NotNull String getAssignableToClassName() {
    Object implementationClassOrName = this.implementationClassOrName;
    if (implementationClassOrName instanceof String) {
      return (String)implementationClassOrName;
    }
    return ((Class<?>)implementationClassOrName).getName();
  }

  @Override
  public final String toString() {
    return "ExtensionComponentAdapter(implementation=" + getAssignableToClassName() + ", plugin=" + pluginDescriptor + ")";
  }
}
