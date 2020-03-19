// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public final class BeanExtensionPoint<T> extends ExtensionPointImpl<T> {
  public BeanExtensionPoint(@NotNull String name,
                     @NotNull String className,
                     @NotNull PluginDescriptor pluginDescriptor,
                     boolean dynamic) {
    super(name, className, pluginDescriptor, dynamic);
  }

  @NotNull
  @Override
  public ExtensionPointImpl<T> cloneFor(@NotNull ComponentManager manager) {
    BeanExtensionPoint<T> result = new BeanExtensionPoint<>(getName(), getClassName(), getPluginDescriptor(), isDynamic());
    result.setComponentManager(manager);
    return result;
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

  @Override
  public void unregisterExtensions(@NotNull ComponentManager componentManager,
                                   @NotNull PluginDescriptor pluginDescriptor,
                                   @NotNull List<Element> elements,
                                   List<Runnable> listenerCallbacks) {
    Map<String, String> defaultAttributes = new HashMap<>();
    ClassLoader classLoader = myDescriptor.getPluginClassLoader();
    if (classLoader == null) {
      classLoader = getClass().getClassLoader();
    }
    try {
      Class<?> aClass = Class.forName(getClassName(), true, classLoader);
      Object defaultInstance;
      if (componentManager.getPicoContainer().getParent() == null) {
        defaultInstance = ReflectionUtil.newInstance(aClass);
      }
      else {
        defaultInstance = componentManager.instantiateClassWithConstructorInjection(aClass, aClass, pluginDescriptor.getPluginId());
      }
      defaultAttributes.putAll(XmlExtensionAdapter.getSerializedDataMap(XmlSerializer.serialize(defaultInstance)));
    }
    catch (ClassNotFoundException ignored) {
    }

    unregisterExtensions((x, adapter) -> {
      if (!(adapter instanceof XmlExtensionAdapter)) {
        return true;
      }
      XmlExtensionAdapter xmlExtensionAdapter = (XmlExtensionAdapter)adapter;
      return xmlExtensionAdapter.getPluginDescriptor() != pluginDescriptor || !xmlExtensionAdapter.isLoadedFromAnyElement(elements, defaultAttributes);
    }, false, listenerCallbacks);
  }
}