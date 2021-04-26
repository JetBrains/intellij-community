// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.components.ComponentConfig
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.extensions.impl.ExtensionDescriptor
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.util.messages.ListenerDescriptor
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
class ContainerDescriptor {
  class ExtensionPointDescriptor(val name: String?,
                                 val qualifiedName: String?,
                                 val `interface`: String?,
                                 val beanClass: String?,
                                 val dynamic: Boolean)

  @JvmField var services: MutableList<ServiceDescriptor>? = null
  @JvmField var components: MutableList<ComponentConfig>? = null
  @JvmField var listeners: MutableList<ListenerDescriptor>? = null

  @Transient
  @JvmField var extensionPoints: MutableList<ExtensionPointImpl<*>>? = null
  @JvmField var extensionPointDescriptors: MutableList<ExtensionPointDescriptor>? = null

  @Transient
  @JvmField var extensions: MutableMap<String, MutableList<ExtensionDescriptor>>? = null

  fun getServices(): List<ServiceDescriptor> = services ?: Collections.emptyList()

  fun addService(serviceDescriptor: ServiceDescriptor) {
    if (services == null) {
      services = ArrayList()
    }
    services!!.add(serviceDescriptor)
  }

  internal fun getComponentListToAdd(): MutableList<ComponentConfig> {
    var result = components
    if (result == null) {
      result = ArrayList()
      components = result
    }
    return result
  }

  override fun toString(): String {
    if (services == null && components == null && extensionPoints == null && extensions == null && listeners == null) {
      return "ContainerDescriptor(empty)"
    }
    else {
      return "ContainerDescriptor(" +
             "services=$services, components=$components, " +
             "extensionPoints=$extensionPoints, extensions=$extensions, listeners=$listeners" +
             ")"
    }
  }
}