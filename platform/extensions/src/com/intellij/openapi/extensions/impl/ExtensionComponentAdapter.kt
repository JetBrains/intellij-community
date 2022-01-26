// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ExtensionComponentAdapter implements LoadingOrder.Orderable {
  public static final ExtensionComponentAdapter[] EMPTY_ARRAY = new ExtensionComponentAdapter[0];

  protected final @NotNull PluginDescriptor pluginDescriptor;

  // Class or String
  @NotNull Object implementationClassOrName;
  protected final ImplementationClassResolver implementationClassResolver;

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

  public abstract @Nullable("if not applicable") <T> T createInstance(@NotNull ComponentManager componentManager);

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
    return getClass().getSimpleName() + "(implementation=" + getAssignableToClassName() + ", plugin=" + pluginDescriptor + ")";
  }
}
