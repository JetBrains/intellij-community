// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.pico.DefaultPicoContainer;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class BeanExtensionPoint<T> extends ExtensionPointImpl<T> implements ImplementationClassResolver {
  public BeanExtensionPoint(@NotNull String name,
                            @NotNull String className,
                            @NotNull PluginDescriptor pluginDescriptor,
                            boolean dynamic) {
    super(name, className, pluginDescriptor, null, dynamic);
  }

  @Override
  public final @NotNull Class<?> resolveImplementationClass(@NotNull ComponentManager componentManager, @NotNull ExtensionComponentAdapter adapter)
    throws ClassNotFoundException {
    return getExtensionClass();
  }

  @Override
  public @NotNull ExtensionPointImpl<T> cloneFor(@NotNull ComponentManager manager) {
    BeanExtensionPoint<T> result = new BeanExtensionPoint<>(getName(), getClassName(), getPluginDescriptor(), isDynamic());
    result.setComponentManager(manager);
    return result;
  }

  @Override
  protected @NotNull ExtensionComponentAdapter createAdapterAndRegisterInPicoContainerIfNeeded(@NotNull Element extensionElement,
                                                                                               @NotNull PluginDescriptor pluginDescriptor,
                                                                                               @NotNull ComponentManager componentManager) {
    String orderId = extensionElement.getAttributeValue("id");
    LoadingOrder order = LoadingOrder.readOrder(extensionElement.getAttributeValue("order"));
    Element effectiveElement = !JDOMUtil.isEmpty(extensionElement) ? extensionElement : null;
    // project level extensions requires Project as constructor argument, so, for now constructor injection disabled only for app level
    if (((DefaultPicoContainer)componentManager.getPicoContainer()).getParent() == null) {
      return new XmlExtensionAdapter(getClassName(), pluginDescriptor, orderId, order, effectiveElement, this);
    }
    return new XmlExtensionAdapter.SimpleConstructorInjectionAdapter(getClassName(), pluginDescriptor, orderId, order, effectiveElement, this);
  }

  @Override
  void unregisterExtensions(@NotNull ComponentManager componentManager,
                            @NotNull PluginDescriptor pluginDescriptor,
                            @NotNull List<Element> elements,
                            @NotNull List<Runnable> priorityListenerCallbacks,
                            @NotNull List<Runnable> listenerCallbacks) {
    unregisterExtensions(adapter -> {
      return adapter.getPluginDescriptor() != pluginDescriptor;
    }, false, priorityListenerCallbacks, listenerCallbacks);
  }
}