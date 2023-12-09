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

  @JvmField var components: MutableList<ComponentConfig>? = null
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

  override fun toString(): String {
    if (_services == null && components.isNullOrEmpty() && extensionPoints.isNullOrEmpty() && extensions.isEmpty() && listeners == null) {
      return "ContainerDescriptor(empty)"
    }
    else {
      return "ContainerDescriptor(" +
             "services=$_services, components=$components, " +
             "extensionPoints=$extensionPoints, extensions=$extensions, listeners=$listeners" +
             ")"
    }
  }
}