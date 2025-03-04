// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser

import com.intellij.ide.plugins.ContainerDescriptor
import com.intellij.ide.plugins.parser.elements.ServiceElement
import com.intellij.openapi.components.ComponentConfig
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import com.intellij.util.messages.ListenerDescriptor

interface ContainerDescriptorBuilder {
  fun addService(serviceElement: ServiceElement)
  fun addComponent(componentConfig: ComponentConfig)
  fun addListener(listenerDescriptor: ListenerDescriptor)
  fun addExtensionPoint(extensionPointDescriptor: ExtensionPointDescriptor)
  fun addExtensionPoints(points: List<ExtensionPointDescriptor>)
  fun removeAllExtensionPoints(): MutableList<ExtensionPointDescriptor>

  fun build(): ContainerDescriptor
}