// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.custom

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.icons.impl.intellij.rendering.SwingIconLayerRenderer
import org.jetbrains.icons.layers.IconLayer
import org.jetbrains.icons.impl.rendering.layers.IconLayerRenderer
import org.jetbrains.icons.legacyIconSupport.SwingIconLayer
import org.jetbrains.icons.rendering.RenderingContext

interface CustomIconLayerRendererProvider {
  fun handles(layer: IconLayer): Boolean
  fun createRenderer(layer: IconLayer, renderingContext: RenderingContext): IconLayerRenderer

  companion object {
    fun createRendererFor(layer: IconLayer, renderingContext: RenderingContext): IconLayerRenderer? {
      for (extension in EP_NAME.extensionList) {
        if (extension.handles(layer)) return SwingIconLayerRenderer(layer as SwingIconLayer, renderingContext)
      }
      return null
    }

    val EP_NAME: ExtensionPointName<CustomIconLayerRendererProvider> = ExtensionPointName("com.intellij.customIconLayerRendererProvider")
  }
}