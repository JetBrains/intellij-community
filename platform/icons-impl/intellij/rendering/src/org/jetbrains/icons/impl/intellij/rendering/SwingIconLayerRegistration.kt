// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.rendering

import kotlinx.serialization.KSerializer
import org.jetbrains.icons.impl.intellij.custom.CustomIconLayerRegistration
import org.jetbrains.icons.legacyIconSupport.SwingIconLayer

internal class SwingIconLayerRegistration: CustomIconLayerRegistration<SwingIconLayer>(SwingIconLayer::class) {
  override fun createSerializer(): KSerializer<SwingIconLayer> {
    return SwingIconLayer.serializer()
  }
}
