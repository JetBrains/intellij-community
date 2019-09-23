// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

final class BeanExtensionPoint<T> extends ExtensionPointImpl<T> {
  BeanExtensionPoint(@NotNull String name,
                     @NotNull String className,
                     @NotNull ComponentManager componentManager,
                     @NotNull PluginDescriptor pluginDescriptor,
                     boolean dynamic) {
    super(name, className, componentManager, pluginDescriptor, dynamic);
  }

  @Override
  @NotNull
  protected ExtensionComponentAdapter createAdapterAndRegisterInPicoContainerIfNeeded(@NotNull Element extensionElement,
                                                                                      @NotNull PluginDescriptor pluginDescriptor,
                                                                                      @NotNull ComponentManager componentManager) {
    // project level extensions requires Project as constructor argument, so, for now constructor injection disabled only for app level
    String orderId = extensionElement.getAttributeValue("id");
    LoadingOrder order = LoadingOrder.readOrder(extensionElement.getAttributeValue("order"));
    Element effectiveElement = !JDOMUtil.isEmpty(extensionElement) ? extensionElement : null;
    if (componentManager.getPicoContainer().getParent() == null) {
      return new XmlExtensionAdapter(getClassName(), pluginDescriptor, orderId, order, effectiveElement);
    }
    return new XmlExtensionAdapter.SimpleConstructorInjectionAdapter(getClassName(), pluginDescriptor, orderId, order, effectiveElement);
  }
}