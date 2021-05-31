// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.components.ComponentConfig
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import com.intellij.util.messages.ListenerDescriptor
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
class ContainerDescriptor {
  private var _services: MutableList<ServiceDescriptor>? = null
  val services: List<ServiceDescriptor>
    get() = _services ?: Collections.emptyList()

  @JvmField var components: MutableList<ComponentConfig>? = null
  @JvmField var listeners: MutableList<ListenerDescriptor>? = null

  @JvmField var extensionPoints: MutableList<ExtensionPointDescriptor>? = null

  @Transient var distinctExtensionPointCount = -1
  @Transient @JvmField var extensions: Map<String, MutableList<ExtensionDescriptor>>? = null

  fun addService(serviceDescriptor: ServiceDescriptor) {
    if (_services == null) {
      _services = ArrayList()
    }
    _services!!.add(serviceDescriptor)
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
    if (_services == null && components == null && extensionPoints == null && extensions == null && listeners == null) {
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