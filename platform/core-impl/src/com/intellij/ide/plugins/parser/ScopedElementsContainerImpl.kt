// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser

import com.intellij.ide.plugins.parser.elements.ComponentElement
import com.intellij.ide.plugins.parser.elements.ExtensionPointElement
import com.intellij.ide.plugins.parser.elements.ListenerElement
import com.intellij.ide.plugins.parser.elements.ServiceElement

internal class ScopedElementsContainerImpl(
  override val services: List<ServiceElement>,
  override val components: List<ComponentElement>,
  override val listeners: List<ListenerElement>,
  override val extensionPoints: List<ExtensionPointElement>,
) : ScopedElementsContainer