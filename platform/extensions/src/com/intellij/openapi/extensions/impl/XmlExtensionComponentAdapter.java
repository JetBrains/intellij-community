// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.pico.CachingConstructorInjectionComponentAdapter;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;
import org.picocontainer.PicoInitializationException;

import java.util.Objects;

class XmlExtensionComponentAdapter extends ExtensionComponentAdapter {
  @Nullable
  private final Element myExtensionElement;

  XmlExtensionComponentAdapter(@NotNull String implementationClassName,
                               @Nullable PicoContainer container,
                               @Nullable PluginDescriptor pluginDescriptor,
                               @Nullable String orderId,
                               @NotNull LoadingOrder order,
                               @Nullable Element extensionElement) {
    super(implementationClassName, container, pluginDescriptor, orderId, order);
    myExtensionElement = extensionElement;
  }

  @Override
  protected void initComponent(@NotNull Object instance) {
    if (myExtensionElement != null) {
      try {
        XmlSerializer.deserializeInto(instance, myExtensionElement);
      }
      catch (Exception e) {
        throw new PicoInitializationException(e);
      }
    }
  }

  static final class ConstructorInjectionAdapter extends XmlExtensionComponentAdapter {
    ConstructorInjectionAdapter(@NotNull String implementationClassName,
                                @Nullable PicoContainer container,
                                @Nullable PluginDescriptor pluginDescriptor,
                                @Nullable String orderId,
                                @NotNull LoadingOrder order, @Nullable Element extensionElement) {
      super(implementationClassName, container, pluginDescriptor, orderId, order, extensionElement);
    }

    @NotNull
    @Override
    protected Object createComponent(@Nullable PicoContainer container, @NotNull Class<?> clazz) {
      return new CachingConstructorInjectionComponentAdapter(getComponentKey(), clazz, null, true)
        .getComponentInstance(Objects.requireNonNull(container));
    }
  }
}
