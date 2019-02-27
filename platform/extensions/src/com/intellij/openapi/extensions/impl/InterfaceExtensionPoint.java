// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.PluginDescriptor;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.MutablePicoContainer;

public final class InterfaceExtensionPoint<T> extends ExtensionPointImpl<T> {
  public InterfaceExtensionPoint(@NotNull String name, @NotNull Class<T> clazz, @NotNull MutablePicoContainer picoContainer) {
    super(name, clazz.getName(), picoContainer, new UndefinedPluginDescriptor());

    myExtensionClass = clazz;
  }

  InterfaceExtensionPoint(@NotNull String name,
                          @NotNull String className,
                          @NotNull MutablePicoContainer picoContainer,
                          @NotNull PluginDescriptor pluginDescriptor) {
    super(name, className, picoContainer, pluginDescriptor);
  }

  @Override
  public synchronized void reset() {
    // we don't check myLoadedAdapters because programmatically loaded extensions are not registered in pico container
    //noinspection NonPrivateFieldAccessedInSynchronizedContext
    for (ExtensionComponentAdapter adapter : myAdapters) {
      if (adapter instanceof ComponentAdapter) {
        myPicoContainer.unregisterComponent(((ComponentAdapter)adapter).getComponentKey());
      }
    }

    super.reset();
  }

  @Override
  @NotNull
  protected ExtensionComponentAdapter createAdapterAndRegisterInPicoContainerIfNeeded(@NotNull Element extensionElement, @NotNull PluginDescriptor pluginDescriptor, @NotNull MutablePicoContainer picoContainer) {
    String implementationClassName = extensionElement.getAttributeValue("implementation");
    if (implementationClassName == null) {
      throw new RuntimeException("'implementation' attribute not specified for '" + getName() + "' extension in '"
                                 + pluginDescriptor.getPluginId() + "' plugin");
    }
    ExtensionComponentAdapter adapter = doCreateAdapter(implementationClassName, extensionElement, shouldDeserializeInstance(extensionElement), pluginDescriptor, true);
    // no need to register bean extension - only InterfaceExtensionPoint registers
    picoContainer.registerComponent((ComponentAdapter)adapter);
    return adapter;
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
