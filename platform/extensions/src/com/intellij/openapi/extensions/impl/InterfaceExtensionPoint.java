// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.ExtensionInstantiationException;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.MutablePicoContainer;

public class InterfaceExtensionPoint<T> extends ExtensionPointImpl<T> {
  public InterfaceExtensionPoint(@NotNull String name, @NotNull Class<T> clazz, @NotNull MutablePicoContainer picoContainer) {
    super(name, clazz.getName(), picoContainer, new UndefinedPluginDescriptor(), false);

    myExtensionClass = clazz;
  }

  InterfaceExtensionPoint(@NotNull String name,
                          @NotNull String className,
                          @NotNull MutablePicoContainer picoContainer,
                          @NotNull PluginDescriptor pluginDescriptor,
                          boolean dynamic) {
    super(name, className, picoContainer, pluginDescriptor, dynamic);
  }

  protected boolean isUsePicoComponentAdapter() {
    return false;
  }

  @Override
  @NotNull
  protected ExtensionComponentAdapter createAdapterAndRegisterInPicoContainerIfNeeded(@NotNull Element extensionElement, @NotNull PluginDescriptor pluginDescriptor, @NotNull MutablePicoContainer picoContainer) {
    String implementationClassName = extensionElement.getAttributeValue("implementation");
    if (implementationClassName == null) {
      throw new ExtensionInstantiationException("'implementation' attribute not specified for '" + getName() + "' extension in '"
                                                + pluginDescriptor.getPluginId() + "' plugin", pluginDescriptor);
    }

    String orderId = extensionElement.getAttributeValue("id");
    LoadingOrder order = LoadingOrder.readOrder(extensionElement.getAttributeValue("order"));
    Element effectiveElement = shouldDeserializeInstance(extensionElement) ? extensionElement : null;
    if (isUsePicoComponentAdapter()) {
      return new XmlExtensionAdapter.ConstructorInjectionAdapter(implementationClassName, pluginDescriptor, orderId, order, effectiveElement);
    }
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

  static final class PicoContainerAwareInterfaceExtensionPoint<T> extends InterfaceExtensionPoint<T> {
    PicoContainerAwareInterfaceExtensionPoint(@NotNull String name,
                                              @NotNull String className,
                                              @NotNull MutablePicoContainer picoContainer,
                                              @NotNull PluginDescriptor pluginDescriptor) {
      super(name, className, picoContainer, pluginDescriptor, false);
    }

    @Override
    protected boolean isUsePicoComponentAdapter() {
      return true;
    }

    @NotNull
    @Override
    protected ExtensionComponentAdapter createAdapterAndRegisterInPicoContainerIfNeeded(@NotNull Element extensionElement,
                                                                                        @NotNull PluginDescriptor pluginDescriptor,
                                                                                        @NotNull MutablePicoContainer picoContainer) {
      ExtensionComponentAdapter adapter = super.createAdapterAndRegisterInPicoContainerIfNeeded(extensionElement, pluginDescriptor, picoContainer);
      picoContainer.registerComponent((ComponentAdapter)adapter);
      return adapter;
    }

    @Override
    public synchronized void reset() {
      //noinspection NonPrivateFieldAccessedInSynchronizedContext
      for (ExtensionComponentAdapter adapter : myAdapters) {
        if (adapter instanceof ComponentAdapter) {
          myPicoContainer.unregisterComponent(((ComponentAdapter)adapter).getComponentKey());
        }
      }

      super.reset();
    }
  }
}
