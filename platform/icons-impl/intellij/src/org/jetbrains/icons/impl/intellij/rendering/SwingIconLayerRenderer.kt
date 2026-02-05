// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.rendering

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.DeferredIcon
import com.intellij.ui.DeferredIconListener
import com.intellij.util.messages.impl.subscribeAsFlow
import kotlinx.coroutines.launch
import org.jetbrains.icons.impl.intellij.custom.CustomIconLayerRendererProvider
import org.jetbrains.icons.impl.rendering.DefaultImageResourceProvider
import org.jetbrains.icons.rendering.Dimensions
import org.jetbrains.icons.rendering.ImageResource
import org.jetbrains.icons.rendering.ImageResourceProvider
import org.jetbrains.icons.impl.rendering.layers.BaseImageIconLayerRenderer
import org.jetbrains.icons.impl.rendering.layers.IconLayerRenderer
import org.jetbrains.icons.rendering.RenderingContext
import org.jetbrains.icons.rendering.ScalingContext
import org.jetbrains.icons.impl.rendering.layers.applyTo
import org.jetbrains.icons.impl.rendering.layers.generateImageModifiers
import org.jetbrains.icons.layers.IconLayer
import org.jetbrains.icons.legacyIconSupport.SwingIconLayer

class SwingIconLayerRendererProvider: CustomIconLayerRendererProvider {
  override fun handles(layer: IconLayer): Boolean {
    return layer is SwingIconLayer
  }

  override fun createRenderer(layer: IconLayer, renderingContext: RenderingContext): IconLayerRenderer {
    val swingLayer = layer as SwingIconLayer
    val renderer = SwingIconLayerRenderer(
      swingLayer,
      renderingContext
    )
    renderer.launchEventBridge()
    return renderer
  }
}

class SwingIconLayerRenderer(
  override val layer: SwingIconLayer,
  override val renderingContext: RenderingContext
): BaseImageIconLayerRenderer() {
  override var image: ImageResource = createImageResource()

  fun launchEventBridge() {
    val icon = layer.legacyIcon
    if (layer.legacyIcon is DeferredIcon) {
      val flow = ApplicationManager.getApplication().messageBus.subscribeAsFlow(DeferredIconListener.TOPIC) {
        object : DeferredIconListener {
          override fun evaluated(deferred: DeferredIcon, result: javax.swing.Icon) {
            if (deferred != icon) return
            trySend(result)
          }
        }
      }
      IconUpdateService.getInstance().scope.launch {
        flow.collect {
          image = createImageResource()
        }
      }
    }
  }

  private fun createImageResource(): ImageResource {
    val provider = ImageResourceProvider.getInstance() as? DefaultImageResourceProvider
    if (provider == null) error("Swing Icon fallback is only supported with DefaultImageResourceProvider")
    return provider.fromSwingIcon(layer.legacyIcon, layer.generateImageModifiers(renderingContext))
  }

  override fun calculateExpectedDimensions(scaling: ScalingContext): Dimensions {
    return Dimensions(scaling.applyTo(image.width) ?: 16, scaling.applyTo(image.height) ?: 16)
  }
}