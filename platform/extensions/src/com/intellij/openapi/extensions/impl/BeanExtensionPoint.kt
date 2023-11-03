// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.PluginDescriptor

internal class BeanExtensionPoint<T : Any>(
  name: String,
  className: String,
  pluginDescriptor: PluginDescriptor,
  componentManager: ComponentManager,
  dynamic: Boolean,
) : ExtensionPointImpl<T>(name = name,
                          className = className,
                          extensionPointPluginDescriptor = pluginDescriptor,
                          componentManager = componentManager,
                          extensionClass = null,
                          isDynamic = dynamic), ImplementationClassResolver {
  override fun resolveImplementationClass(componentManager: ComponentManager, adapter: ExtensionComponentAdapter): Class<T> {
    return getExtensionClass()
  }

  override fun createAdapter(descriptor: ExtensionDescriptor,
                             pluginDescriptor: PluginDescriptor,
                             componentManager: ComponentManager): ExtensionComponentAdapter {
    if (componentManager.isInjectionForExtensionSupported) {
      return SimpleConstructorInjectionAdapter(implementationClassName = className,
                                               pluginDescriptor = pluginDescriptor,
                                               descriptor = descriptor,
                                               implementationClassResolver = this)
    }
    else {
      return XmlExtensionAdapter(implementationClassName = className,
                                 pluginDescriptor = pluginDescriptor,
                                 orderId = descriptor.orderId,
                                 order = descriptor.order,
                                 extensionElement = descriptor.element,
                                 implementationClassResolver = this)
    }
  }

  override fun unregisterExtensions(componentManager: ComponentManager,
                                    pluginDescriptor: PluginDescriptor,
                                    priorityListenerCallbacks: MutableList<in Runnable>,
                                    listenerCallbacks: MutableList<in Runnable>) {
    unregisterExtensions(stopAfterFirstMatch = false,
                         priorityListenerCallbacks = priorityListenerCallbacks,
                         listenerCallbacks = listenerCallbacks,
                         extensionToKeepFilter = { it.pluginDescriptor !== pluginDescriptor })
  }
}
