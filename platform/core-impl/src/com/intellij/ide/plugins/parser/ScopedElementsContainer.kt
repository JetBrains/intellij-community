// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser

import com.intellij.ide.plugins.parser.elements.ComponentElement
import com.intellij.ide.plugins.parser.elements.ExtensionPointElement
import com.intellij.ide.plugins.parser.elements.ListenerElement
import com.intellij.ide.plugins.parser.elements.ServiceElement

interface ScopedElementsContainer {
  val services: List<ServiceElement>
  val components: List<ComponentElement>
  val listeners: List<ListenerElement>
  val extensionPoints: List<ExtensionPointElement>
}