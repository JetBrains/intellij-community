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

  private var _listeners: MutableList<ListenerDescriptor>? = null
  val listeners: List<ListenerDescriptor>
    get() = _listeners ?: Java11Shim.INSTANCE.listOf()

  private var _extensionPoints: MutableList<ExtensionPointDescriptor>? = null
  val extensionPoints: List<ExtensionPointDescriptor>
    get() = _extensionPoints ?: Java11Shim.INSTANCE.listOf()

  @Transient var distinctExtensionPointCount: Int = -1
  @Transient @JvmField var extensions: Map<String, List<ExtensionDescriptor>> = Java11Shim.INSTANCE.mapOf()

  internal fun addService(serviceDescriptor: ServiceDescriptor) {
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

  internal fun addListener(listenerDescriptor: ListenerDescriptor) {
    if (_listeners == null) {
      _listeners = ArrayList()
    }
    _listeners!!.add(listenerDescriptor)
  }

  internal fun addExtensionPoint(extensionPointDescriptor: ExtensionPointDescriptor) {
    if (_extensionPoints == null) {
      _extensionPoints = ArrayList()
    }
    _extensionPoints!!.add(extensionPointDescriptor)
  }

  internal fun addExtensionPoints(points: List<ExtensionPointDescriptor>) {
    if (_extensionPoints == null) {
      _extensionPoints = ArrayList(points)
    } else {
      _extensionPoints!!.addAll(points)
    }
  }

  override fun toString(): String {
    if (_services == null && components.isEmpty() && _extensionPoints.isNullOrEmpty() && extensions.isEmpty() && _listeners == null) {
      return "ContainerDescriptor(empty)"
    }
    else {
      return "ContainerDescriptor(" +
             "services=$_services, components=$_components, " +
             "extensionPoints=$_extensionPoints, extensions=$extensions, listeners=$_listeners" +
             ")"
    }
  }
}