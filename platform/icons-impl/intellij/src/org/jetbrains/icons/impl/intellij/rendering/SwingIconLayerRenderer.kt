// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.rendering

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.ui.DeferredIcon
import com.intellij.ui.DeferredIconListener
import com.intellij.util.messages.impl.subscribeAsFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.icons.impl.intellij.rendering.custom.CustomIconLayerRendererProvider
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
import org.jetbrains.icons.rendering.PaintingApi
import org.jetbrains.icons.swing.SwingIconLayer
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon
import kotlin.coroutines.resume

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
    return renderer
  }
}

class SwingIconLayerRenderer(
  override val layer: SwingIconLayer,
  override val renderingContext: RenderingContext
): BaseImageIconLayerRenderer() {
  private val deferredIcon = layer.legacyIcon as? DeferredIcon?
  private var isDone = deferredIcon?.isDone ?: false
  override var image: ImageResource = createImageResource()
  private val isPending = AtomicBoolean(false)

  override fun render(api: PaintingApi) {
    checkForUpdatesIfNeeded()
    super.render(api)
  }

  private fun checkForUpdatesIfNeeded() {
    if (deferredIcon != null && !PowerSaveMode.isEnabled() && !isDone) {
      if (!isPending.getAndSet(true)) {
        if (!deferredIcon.isDone) {
          IconUpdateService.getInstance().scope.launch {
            withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
              suspendUntilDeferredIconIsDone()
              isDone = true
              image = createImageResource()
            }
          }
        } else {
          isDone = true
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

  private suspend fun suspendUntilDeferredIconIsDone() {
    val connection = ApplicationManager.getApplication().messageBus.simpleConnect()
    try {
      suspendCancellableCoroutine { continuation ->
        val listener = object : DeferredIconListener {
          override fun evaluated(deferred: DeferredIcon, result: Icon) {
            if (deferred === this@SwingIconLayerRenderer.deferredIcon) {
              continuation.resume(Unit)
            }
          }
        }
        connection.subscribe(DeferredIconListener.TOPIC, listener)
      }
    }
    finally {
      connection.disconnect()
    }
  }
}