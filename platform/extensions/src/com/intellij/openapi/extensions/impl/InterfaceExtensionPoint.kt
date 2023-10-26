// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.PluginDescriptor

internal class InterfaceExtensionPoint<T : Any>(
  name: String,
  className: String,
  pluginDescriptor: PluginDescriptor,
  componentManager: ComponentManager,
  clazz: Class<T>?,
  dynamic: Boolean,
  private val hasAttributes: Boolean,
) : ExtensionPointImpl<T>(name, className, pluginDescriptor, componentManager, clazz, dynamic) {
  override fun createAdapter(descriptor: ExtensionDescriptor,
                             pluginDescriptor: PluginDescriptor,
                             componentManager: ComponentManager): ExtensionComponentAdapter {
    val implementationClassName = descriptor.implementation
                                  ?: throw componentManager.createError(
                                    "Attribute \"implementation\" is not specified for \"$name\" extension",
                                    pluginDescriptor.pluginId)

    if (hasAttributes) {
      val customAttributes = if (descriptor.hasExtraAttributes) {
        descriptor.element?.attributes ?: emptyMap()
      }
      else {
        emptyMap()
      }
      descriptor.element = null
      return AdapterWithCustomAttributes(implementationClassName = implementationClassName,
                                         pluginDescriptor = pluginDescriptor,
                                         descriptor = descriptor,
                                         customAttributes = customAttributes,
                                         implementationClassResolver = InterfaceExtensionImplementationClassResolver)
    }
    else {
      // see comment in readExtensions WHY an element maybe created for interface extension point adapter
      // we cannot nullify an element as part of readExtensions - in readExtensions not yet clear is it bean or interface extension
      if (!descriptor.hasExtraAttributes && descriptor.element != null && descriptor.element!!.children.isEmpty()) {
        descriptor.element = null
      }
    }

    return SimpleConstructorInjectionAdapter(implementationClassName = implementationClassName,
                                             pluginDescriptor = pluginDescriptor,
                                             descriptor = descriptor,
                                             implementationClassResolver = InterfaceExtensionImplementationClassResolver)
  }

  override fun unregisterExtensions(componentManager: ComponentManager,
                                    pluginDescriptor: PluginDescriptor,
                                    priorityListenerCallbacks: MutableList<in Runnable>,
                                    listenerCallbacks: MutableList<in Runnable>) {
    unregisterExtensions(false, priorityListenerCallbacks, listenerCallbacks) { it.pluginDescriptor !== pluginDescriptor }
  }
}

internal class AdapterWithCustomAttributes(
  implementationClassName: String,
  pluginDescriptor: PluginDescriptor,
  descriptor: ExtensionDescriptor,
  implementationClassResolver: ImplementationClassResolver,
  @JvmField val customAttributes: Map<String, String>,
) : XmlExtensionAdapter(
  implementationClassName = implementationClassName,
  pluginDescriptor = pluginDescriptor,
  orderId = descriptor.orderId,
  order = descriptor.order,
  extensionElement = descriptor.element,
  implementationClassResolver = implementationClassResolver,
) {
  override fun <T> instantiateClass(aClass: Class<T>, componentManager: ComponentManager): T {
    return componentManager.instantiateClassWithConstructorInjection(aClass, aClass, pluginDescriptor.pluginId)
  }
}