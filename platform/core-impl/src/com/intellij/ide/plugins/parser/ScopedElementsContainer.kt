// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser

import com.intellij.ide.plugins.ContainerDescriptor
import com.intellij.ide.plugins.parser.elements.ComponentElement
import com.intellij.ide.plugins.parser.elements.ComponentElement.Companion.convert
import com.intellij.ide.plugins.parser.elements.ExtensionPointElement
import com.intellij.ide.plugins.parser.elements.ExtensionPointElement.Companion.convert
import com.intellij.ide.plugins.parser.elements.ListenerElement
import com.intellij.ide.plugins.parser.elements.ListenerElement.Companion.convert
import com.intellij.ide.plugins.parser.elements.ServiceElement
import com.intellij.ide.plugins.parser.elements.ServiceElement.Companion.convert

interface ScopedElementsContainer {
  val services: List<ServiceElement>
  val components: List<ComponentElement>
  val listeners: List<ListenerElement>
  val extensionPoints: List<ExtensionPointElement>
}

fun ScopedElementsContainer.convert(): ContainerDescriptor = ContainerDescriptor(
  services = services.map { it.convert() },
  components = components.map { it.convert() } ,
  listeners = listeners.map { it.convert() },
  extensionPoints = extensionPoints.map { it.convert() },
)