// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.components.ComponentConfig
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import com.intellij.util.Java11Shim
import com.intellij.util.messages.ListenerDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ContainerDescriptor {
  private var _services: MutableList<ServiceDescriptor>? = null
  val services: List<ServiceDescriptor>
    get() = _services ?: Java11Shim.INSTANCE.listOf()

  private var _components: MutableList<ComponentConfig>? = null
  val components: List<ComponentConfig>
    get() = _components ?: Java11Shim.INSTANCE.listOf()

  @JvmField var listeners: MutableList<ListenerDescriptor>? = null

  @JvmField var extensionPoints: MutableList<ExtensionPointDescriptor>? = null

  @Transient var distinctExtensionPointCount: Int = -1
  @Transient @JvmField var extensions: Map<String, List<ExtensionDescriptor>> = Java11Shim.INSTANCE.mapOf()

  fun addService(serviceDescriptor: ServiceDescriptor) {
    if (_services == null) {
      _services = ArrayList()
    }
    _services!!.add(serviceDescriptor)
  }

  internal fun addComponent(componentConfig: ComponentConfig) {
    if (_components == null) {
      _components = ArrayList()
    }
    _components!!.add(componentConfig)
  }

  override fun toString(): String {
    if (_services == null && components.isEmpty() && extensionPoints.isNullOrEmpty() && extensions.isEmpty() && listeners == null) {
      return "ContainerDescriptor(empty)"
    }
    else {
      return "ContainerDescriptor(" +
             "services=$_services, components=$_components, " +
             "extensionPoints=$extensionPoints, extensions=$extensions, listeners=$listeners" +
             ")"
    }
  }
}