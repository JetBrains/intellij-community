// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public final class InterfaceExtensionPoint<T> extends ExtensionPointImpl<T> {
  InterfaceExtensionPoint(@NotNull String name, @NotNull Class<T> clazz, @NotNull PluginDescriptor pluginDescriptor) {
    super(name, clazz.getName(), pluginDescriptor, clazz, false);
  }

  public InterfaceExtensionPoint(@NotNull String name,
                                 @NotNull String className,
                                 @NotNull PluginDescriptor pluginDescriptor,
                                 boolean dynamic) {
    super(name, className, pluginDescriptor, null, dynamic);
  }

  @Override
  public @NotNull ExtensionPointImpl<T> cloneFor(@NotNull ComponentManager manager) {
    InterfaceExtensionPoint<T> result = new InterfaceExtensionPoint<>(getName(), getClassName(), getPluginDescriptor(), isDynamic());
    result.setComponentManager(manager);
    return result;
  }

  @Override
  protected @NotNull ExtensionComponentAdapter createAdapterAndRegisterInPicoContainerIfNeeded(@NotNull Element extensionElement, @NotNull PluginDescriptor pluginDescriptor, @NotNull ComponentManager componentManager) {
    String implementationClassName = extensionElement.getAttributeValue("implementation");
    if (implementationClassName == null) {
      // deprecated
      implementationClassName = extensionElement.getAttributeValue("implementationClass");
      if (implementationClassName == null) {
        throw componentManager.createError("Attribute \"implementation\" is not specified for \"" + getName() + "\" extension", pluginDescriptor.getPluginId());
      }
    }

    String orderId = extensionElement.getAttributeValue("id");
    LoadingOrder order = LoadingOrder.readOrder(extensionElement.getAttributeValue("order"));
    Element effectiveElement = shouldDeserializeInstance(extensionElement) ? extensionElement : null;
    return new XmlExtensionAdapter.SimpleConstructorInjectionAdapter(implementationClassName, pluginDescriptor, orderId, order, effectiveElement);
  }

  @Override
  void unregisterExtensions(@NotNull ComponentManager componentManager,
                            @NotNull PluginDescriptor pluginDescriptor,
                            @NotNull List<Element> elements,
                            @NotNull List<Runnable> priorityListenerCallbacks,
                            @NotNull List<Runnable> listenerCallbacks) {
    Set<String> implementationClassNames = new HashSet<>();
    for (Element element : elements) {
      implementationClassNames.add(element.getAttributeValue("implementation"));
    }
    unregisterExtensions((x, adapter) -> {
      return !implementationClassNames.contains(adapter.getAssignableToClassName());
    }, false, priorityListenerCallbacks, listenerCallbacks);
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
