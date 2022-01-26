// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.ExtensionDescriptor;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public final class InterfaceExtensionPoint<T> extends ExtensionPointImpl<T> {
  public InterfaceExtensionPoint(@NotNull String name,
                                 @NotNull String className,
                                 @NotNull PluginDescriptor pluginDescriptor,
                                 @NotNull ComponentManager componentManager,
                                 @Nullable Class<T> clazz,
                                 boolean dynamic) {
    super(name, className, pluginDescriptor, componentManager, clazz, dynamic);
  }

  @Override
  @NotNull ExtensionComponentAdapter createAdapter(@NotNull ExtensionDescriptor descriptor,
                                                   @NotNull PluginDescriptor pluginDescriptor,
                                                   @NotNull ComponentManager componentManager) {
    // see comment in readExtensions WHY element maybe created for interface extension point adapter
    descriptor.element = null;
    String implementationClassName = descriptor.implementation;
    if (implementationClassName == null) {
      throw componentManager.createError("Attribute \"implementation\" is not specified for \"" + getName() + "\" extension",
                                         pluginDescriptor.getPluginId());
    }

    return new XmlExtensionAdapter.SimpleConstructorInjectionAdapter(implementationClassName, pluginDescriptor,
                                                                     descriptor.orderId, descriptor.order,
                                                                     null,
                                                                     InterfaceExtensionImplementationClassResolver.INSTANCE);
  }

  @Override
  void unregisterExtensions(@NotNull ComponentManager componentManager,
                            @NotNull PluginDescriptor pluginDescriptor,
                            @NotNull List<ExtensionDescriptor> elements,
                            @NotNull List<Runnable> priorityListenerCallbacks,
                            @NotNull List<Runnable> listenerCallbacks) {
    unregisterExtensions(adapter -> adapter.getPluginDescriptor() != pluginDescriptor, false, priorityListenerCallbacks, listenerCallbacks);
  }
}
