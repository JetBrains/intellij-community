// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.components.ComponentConfig
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import com.intellij.util.messages.ListenerDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ContainerDescriptor(
  @JvmField
  val services: List<ServiceDescriptor>,
  @JvmField
  val components: List<ComponentConfig>,
  @JvmField
  val listeners: List<ListenerDescriptor>,
  @JvmField
  val extensionPoints: List<ExtensionPointDescriptor>,
) {
  override fun toString(): String {
    if (services.isEmpty() && components.isEmpty() && extensionPoints.isEmpty() && listeners.isEmpty()) {
      return "ContainerDescriptor(empty)"
    }
    else {
      return "ContainerDescriptor(" +
             "services=$services, components=$components, " +
             "extensionPoints=$extensionPoints, listeners=$listeners" +
             ")"
    }
  }
}