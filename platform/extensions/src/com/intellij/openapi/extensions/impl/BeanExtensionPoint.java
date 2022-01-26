// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.ExtensionDescriptor;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class BeanExtensionPoint<T> extends ExtensionPointImpl<T> implements ImplementationClassResolver {
  public BeanExtensionPoint(@NotNull String name,
                            @NotNull String className,
                            @NotNull PluginDescriptor pluginDescriptor,
                            @NotNull ComponentManager componentManager,
                            boolean dynamic) {
    super(name, className, pluginDescriptor, componentManager, null, dynamic);
  }

  @Override
  public @NotNull Class<?> resolveImplementationClass(@NotNull ComponentManager componentManager, @NotNull ExtensionComponentAdapter adapter)
    throws ClassNotFoundException {
    return getExtensionClass();
  }

  @Override
  @NotNull ExtensionComponentAdapter createAdapter(@NotNull ExtensionDescriptor descriptor,
                                                   @NotNull PluginDescriptor pluginDescriptor,
                                                   @NotNull ComponentManager componentManager) {
    if (componentManager.isInjectionForExtensionSupported()) {
      return new XmlExtensionAdapter.SimpleConstructorInjectionAdapter(getClassName(), pluginDescriptor, descriptor.orderId,
                                                                       descriptor.order,
                                                                       descriptor.element, this);
    }
    else {
      return new XmlExtensionAdapter(getClassName(), pluginDescriptor, descriptor.orderId, descriptor.order, descriptor.element, this);
    }
  }

  @Override
  void unregisterExtensions(@NotNull ComponentManager componentManager,
                            @NotNull PluginDescriptor pluginDescriptor,
                            @NotNull List<ExtensionDescriptor> elements,
                            @NotNull List<Runnable> priorityListenerCallbacks,
                            @NotNull List<Runnable> listenerCallbacks) {
    unregisterExtensions(adapter -> {
      return adapter.getPluginDescriptor() != pluginDescriptor;
    }, false, priorityListenerCallbacks, listenerCallbacks);
  }
}