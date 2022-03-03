// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.impl.XmlExtensionAdapter.SimpleConstructorInjectionAdapter

internal class BeanExtensionPoint<T>(name: String,
                                     className: String,
                                     pluginDescriptor: PluginDescriptor,
                                     componentManager: ComponentManager,
                                     dynamic: Boolean) : ExtensionPointImpl<T>(name, className, pluginDescriptor, componentManager, null,
                                                                               dynamic), ImplementationClassResolver {
  override fun resolveImplementationClass(componentManager: ComponentManager, adapter: ExtensionComponentAdapter) = extensionClass

  override fun createAdapter(descriptor: ExtensionDescriptor,
                             pluginDescriptor: PluginDescriptor,
                             componentManager: ComponentManager): ExtensionComponentAdapter {
    return if (componentManager.isInjectionForExtensionSupported) {
      SimpleConstructorInjectionAdapter(className, pluginDescriptor, descriptor, this)
    }
    else {
      XmlExtensionAdapter(className, pluginDescriptor, descriptor.orderId, descriptor.order, descriptor.element, this)
    }
  }

  override fun unregisterExtensions(componentManager: ComponentManager,
                                    pluginDescriptor: PluginDescriptor,
                                    elements: List<ExtensionDescriptor>,
                                    priorityListenerCallbacks: List<Runnable>,
                                    listenerCallbacks: List<Runnable>) {
    unregisterExtensions(false, priorityListenerCallbacks, listenerCallbacks) { it.pluginDescriptor !== pluginDescriptor }
  }
}