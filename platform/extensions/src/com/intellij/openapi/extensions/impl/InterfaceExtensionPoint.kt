// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.impl.XmlExtensionAdapter.SimpleConstructorInjectionAdapter

internal class InterfaceExtensionPoint<T>(name: String,
                                          className: String,
                                          pluginDescriptor: PluginDescriptor,
                                          componentManager: ComponentManager,
                                          clazz: Class<T>?,
                                          dynamic: Boolean) : ExtensionPointImpl<T>(name, className, pluginDescriptor, componentManager,
                                                                                    clazz, dynamic) {
  public override fun createAdapter(descriptor: ExtensionDescriptor,
                                    pluginDescriptor: PluginDescriptor,
                                    componentManager: ComponentManager): ExtensionComponentAdapter {
    // see comment in readExtensions WHY element maybe created for interface extension point adapter
    // we cannot nullify element as part of readExtensions - in readExtensions not yet clear is it bean or interface extension
    if (!descriptor.hasExtraAttributes && descriptor.element != null && descriptor.element!!.children.isEmpty()) {
      descriptor.element = null
    }
    val implementationClassName = descriptor.implementation
                                  ?: throw componentManager.createError(
                                    "Attribute \"implementation\" is not specified for \"$name\" extension",
                                    pluginDescriptor.pluginId)
    return SimpleConstructorInjectionAdapter(implementationClassName, pluginDescriptor, descriptor,
                                             InterfaceExtensionImplementationClassResolver.INSTANCE)
  }

  public override fun unregisterExtensions(componentManager: ComponentManager,
                                           pluginDescriptor: PluginDescriptor,
                                           elements: List<ExtensionDescriptor>,
                                           priorityListenerCallbacks: List<Runnable>,
                                           listenerCallbacks: List<Runnable>) {
    unregisterExtensions(false, priorityListenerCallbacks, listenerCallbacks) { it.pluginDescriptor !== pluginDescriptor }
  }
}