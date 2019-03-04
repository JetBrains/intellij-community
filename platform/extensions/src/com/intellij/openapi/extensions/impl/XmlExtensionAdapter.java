// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.pico.AssignableToComponentAdapter;
import com.intellij.util.pico.CachingConstructorInjectionComponentAdapter;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;
import org.picocontainer.PicoInitializationException;
import org.picocontainer.PicoIntrospectionException;
import org.picocontainer.PicoVisitor;

import java.util.Objects;

class XmlExtensionAdapter extends ExtensionComponentAdapter {
  @Nullable
  private final Element myExtensionElement;

  protected Object myComponentInstance;

  XmlExtensionAdapter(@NotNull String implementationClassName,
                      @Nullable PluginDescriptor pluginDescriptor,
                      @Nullable String orderId,
                      @NotNull LoadingOrder order,
                      @Nullable Element extensionElement) {
    super(implementationClassName, pluginDescriptor, orderId, order);

    myExtensionElement = extensionElement;
  }

  @Override
  boolean isInstanceCreated() {
    return myComponentInstance != null;
  }

  @NotNull
  @Override
  public Object createInstance(@Nullable PicoContainer container) {
    Object instance = myComponentInstance;
    if (instance != null) {
      // todo add assert that createInstance was already called
      // problem is that ExtensionPointImpl clears cache on runtime modification and so adapter instance need to be recreated
      // it will be addressed later, for now better to reduce scope of changes
      return instance;
    }

    instance = super.createInstance(container);
    myComponentInstance = instance;
    return instance;
  }

  @Override
  protected void initInstance(@NotNull Object instance) {
    if (myExtensionElement != null) {
      try {
        XmlSerializer.deserializeInto(instance, myExtensionElement);
      }
      catch (Exception e) {
        throw new PicoInitializationException(e);
      }
    }
  }

  private static class PicoComponentAdapter extends XmlExtensionAdapter implements AssignableToComponentAdapter {
    PicoComponentAdapter(@NotNull String implementationClassName,
                         @Nullable PluginDescriptor pluginDescriptor,
                         @Nullable String orderId,
                         @NotNull LoadingOrder order,
                         @Nullable Element extensionElement) {
      super(implementationClassName, pluginDescriptor, orderId, order, extensionElement);
    }

    @Override
    @NotNull
    public final Object getComponentInstance(@Nullable PicoContainer container) {
      Object instance = myComponentInstance;
      return instance == null ? createInstance(container) : instance;
    }

    @Override
    public final Class getComponentImplementation() {
      return getImplementationClass();
    }

    @Override
    public final Object getComponentKey() {
      return this;
    }

    @Override
    public final void verify(PicoContainer container) throws PicoIntrospectionException {
      throw new UnsupportedOperationException("Method verify is not supported in " + getClass());
    }

    @Override
    public final void accept(PicoVisitor visitor) {
      throw new UnsupportedOperationException("Method accept is not supported in " + getClass());
    }
  }

  static final class ConstructorInjectionAdapter extends PicoComponentAdapter {
    ConstructorInjectionAdapter(@NotNull String implementationClassName,
                                @Nullable PluginDescriptor pluginDescriptor,
                                @Nullable String orderId,
                                @NotNull LoadingOrder order, @Nullable Element extensionElement) {
      super(implementationClassName, pluginDescriptor, orderId, order, extensionElement);
    }

    @NotNull
    @Override
    protected Object instantiateClass(@NotNull Class<?> clazz, @Nullable PicoContainer container) {
      return new CachingConstructorInjectionComponentAdapter(getComponentKey(), clazz, null, true)
        .getComponentInstance(Objects.requireNonNull(container));
    }
  }
}
