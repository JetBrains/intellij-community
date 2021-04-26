// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public final class InterfaceExtensionPoint<T> extends ExtensionPointImpl<T> {
  public InterfaceExtensionPoint(@NotNull String name,
                                 @NotNull String className,
                                 @NotNull PluginDescriptor pluginDescriptor,
                                 @Nullable Class<T> clazz,
                                 boolean dynamic) {
    super(name, className, pluginDescriptor, clazz, dynamic);
  }

  @Override
  public @NotNull ExtensionPointImpl<T> cloneFor(@NotNull ComponentManager manager) {
    InterfaceExtensionPoint<T> result = new InterfaceExtensionPoint<>(getName(), getClassName(), getPluginDescriptor(), null, isDynamic());
    result.setComponentManager(manager);
    return result;
  }

  @Override
  protected @NotNull ExtensionComponentAdapter createAdapter(@NotNull ExtensionDescriptor descriptor, @NotNull PluginDescriptor pluginDescriptor, @NotNull ComponentManager componentManager) {
    Element element = descriptor.element;
    assert element != null;
    String implementationClassName = element.getAttributeValue("implementation");
    if (implementationClassName == null) {
      // deprecated
      implementationClassName = element.getAttributeValue("implementationClass");
      if (implementationClassName == null) {
        throw componentManager.createError("Attribute \"implementation\" is not specified for \"" + getName() + "\" extension", pluginDescriptor.getPluginId());
      }
    }

    LoadingOrder order = LoadingOrder.readOrder(descriptor.order);
    Element effectiveElement = shouldDeserializeInstance(element) ? element : null;
    return new XmlExtensionAdapter.SimpleConstructorInjectionAdapter(implementationClassName, pluginDescriptor, descriptor.orderId, order,
                                                                     effectiveElement,
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

  private static boolean shouldDeserializeInstance(@NotNull Element extensionElement) {
    // has content
    if (!extensionElement.getContent().isEmpty()) {
      return true;
    }

    // has custom attributes
    for (Attribute attribute : extensionElement.getAttributes()) {
      String name = attribute.getName();
      if (!("implementation".equals(name) ||
            "implementationClass".equals(name) ||
            "id".equals(name) ||
            "order".equals(name) ||
            "os".equals(name))) {
        return true;
      }
    }
    return false;
  }
}
