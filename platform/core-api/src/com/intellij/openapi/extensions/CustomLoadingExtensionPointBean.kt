// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions

import com.intellij.openapi.components.ComponentManager
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.TestOnly

abstract class CustomLoadingExtensionPointBean<T : Any> : BaseKeyedLazyInstance<T> {
  @Attribute
  @JvmField
  var factoryClass: String? = null

  @Attribute
  @JvmField
  var factoryArgument: String? = null

  protected constructor()

  @TestOnly
  protected constructor(instance: T) : super(instance)

  fun createInstance(componentManager: ComponentManager): T {
    val instance: T = if (factoryClass == null) {
      super.createInstance(componentManager, pluginDescriptor)
    }
    else {
      val factory = componentManager.instantiateClass<ExtensionFactory>(factoryClass!!, pluginDescriptor)
      @Suppress("UNCHECKED_CAST")
      factory.createInstance(factoryArgument!!, implementationClassName) as T
    }
    (instance as? PluginAware)?.setPluginDescriptor(pluginDescriptor)
    return instance
  }
}