// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser

import com.intellij.ide.plugins.ContainerDescriptor
import com.intellij.ide.plugins.parser.elements.ComponentElement
import com.intellij.ide.plugins.parser.elements.ComponentElement.Companion.convert
import com.intellij.ide.plugins.parser.elements.ServiceElement
import com.intellij.ide.plugins.parser.elements.ServiceElement.Companion.convert
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import com.intellij.util.Java11Shim
import com.intellij.util.messages.ListenerDescriptor

internal class ContainerDescriptorBuilderMemoryOptimized : ContainerDescriptorBuilder {
  private var _services: MutableList<ServiceElement>? = null
  private var _listeners: MutableList<ListenerDescriptor>? = null
  private var _extensionPoints: MutableList<ExtensionPointDescriptor>? = null
  private var _components: MutableList<ComponentElement>? = null

  override fun addService(serviceElement: ServiceElement) {
    if (_services == null) {
      _services = ArrayList()
    }
    _services!!.add(serviceElement)
  }

  override fun addComponent(componentElement: ComponentElement) {
    if (_components == null) {
      _components = ArrayList()
    }
    _components!!.add(componentElement)
  }

  override fun addListener(listenerDescriptor: ListenerDescriptor) {
    if (_listeners == null) {
      _listeners = ArrayList()
    }
    _listeners!!.add(listenerDescriptor)
  }

  override fun addExtensionPoint(extensionPointDescriptor: ExtensionPointDescriptor) {
    if (_extensionPoints == null) {
      _extensionPoints = ArrayList()
    }
    _extensionPoints!!.add(extensionPointDescriptor)
  }

  override fun addExtensionPoints(points: List<ExtensionPointDescriptor>) {
    if (_extensionPoints == null) {
      _extensionPoints = ArrayList(points)
    } else {
      _extensionPoints!!.addAll(points)
    }
  }

  override fun removeAllExtensionPoints(): MutableList<ExtensionPointDescriptor> {
    val result = _extensionPoints ?: ArrayList()
    _extensionPoints = null
    return result
  }

  override fun build(): ContainerDescriptor {
    val container = ContainerDescriptor(
      _services?.map { it.convert() } ?: Java11Shim.INSTANCE.listOf(),
      _components?.map { it.convert() } ?: Java11Shim.INSTANCE.listOf(),
      _listeners ?: Java11Shim.INSTANCE.listOf(),
      _extensionPoints ?: Java11Shim.INSTANCE.listOf(),
    )
    _services = null
    _components = null
    _listeners = null
    _extensionPoints = null
    return container
  }
}