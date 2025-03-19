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
class ContainerDescriptor(
  val services: List<ServiceDescriptor>,
  val components: List<ComponentConfig>,
  val listeners: List<ListenerDescriptor>,
  val extensionPoints: List<ExtensionPointDescriptor>,
) {
  @Transient var distinctExtensionPointCount: Int = -1
  @Transient @JvmField var extensions: Map<String, List<ExtensionDescriptor>> = Java11Shim.INSTANCE.mapOf()

  override fun toString(): String {
    if (services.isEmpty() && components.isEmpty() && extensionPoints.isEmpty() && extensions.isEmpty() && listeners.isEmpty()) {
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