// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

final class InterfaceExtensionPoint<T> extends ExtensionPointImpl<T> {
  InterfaceExtensionPoint(@NotNull String name, @NotNull Class<T> clazz, @NotNull ComponentManager componentManager) {
    super(name, clazz.getName(), componentManager, new UndefinedPluginDescriptor(), false);

    myExtensionClass = clazz;
  }

  InterfaceExtensionPoint(@NotNull String name,
                          @NotNull String className,
                          @NotNull ComponentManager componentManager,
                          @NotNull PluginDescriptor pluginDescriptor,
                          boolean dynamic) {
    super(name, className, componentManager, pluginDescriptor, dynamic);
  }

  @Override
  @NotNull
  protected ExtensionComponentAdapter createAdapterAndRegisterInPicoContainerIfNeeded(@NotNull Element extensionElement, @NotNull PluginDescriptor pluginDescriptor, @NotNull ComponentManager componentManager) {
    String implementationClassName = extensionElement.getAttributeValue("implementation");
    if (implementationClassName == null) {
      throw componentManager.createError("Attribute \"implementation\" is not specified for \"" + getName() + "\" extension", pluginDescriptor.getPluginId());
    }

    String orderId = extensionElement.getAttributeValue("id");
    LoadingOrder order = LoadingOrder.readOrder(extensionElement.getAttributeValue("order"));
    Element effectiveElement = shouldDeserializeInstance(extensionElement) ? extensionElement : null;
    return new XmlExtensionAdapter.SimpleConstructorInjectionAdapter(implementationClassName, pluginDescriptor, orderId, order, effectiveElement);
  }

  private static boolean shouldDeserializeInstance(@NotNull Element extensionElement) {
    // has content
    if (!extensionElement.getContent().isEmpty()) {
      return true;
    }

    // has custom attributes
    for (Attribute attribute : extensionElement.getAttributes()) {
      final String name = attribute.getName();
      if (!"implementation".equals(name) && !"id".equals(name) && !"order".equals(name) && !"os".equals(name)) {
        return true;
      }
    }
    return false;
  }
}
