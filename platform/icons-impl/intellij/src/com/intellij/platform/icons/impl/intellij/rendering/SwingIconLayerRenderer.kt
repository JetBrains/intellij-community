// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.ui.DeferredIcon
import com.intellij.ui.DeferredIconListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.intellij.platform.icons.impl.rendering.DefaultImageResourceProvider
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.impl.rendering.layers.BaseImageIconLayerRenderer
import com.intellij.platform.icons.impl.rendering.layers.generateImageModifiers
import com.intellij.platform.icons.rendering.LayerPaintingContext
import com.intellij.platform.icons.impl.layers.SwingIconLayer
import com.intellij.platform.icons.impl.rendering.DefaultRenderingContext
import com.intellij.platform.icons.impl.rendering.layers.LayerLayout
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon
import kotlin.coroutines.resume

class SwingIconLayerRenderer(
  override val layer: SwingIconLayer,
  override val renderingContext: DefaultRenderingContext
): BaseImageIconLayerRenderer() {
  private val deferredIcon = layer.legacyIcon as? DeferredIcon?
  private var isDone = deferredIcon?.isDone ?: false
  override var image: ImageResource = createImageResource()
  override var layout: LayerLayout = applyLayout(image)
  private val isPending = AtomicBoolean(false)

  override fun render(paintingContext: LayerPaintingContext) {
    checkForUpdatesIfNeeded()
    super.render(paintingContext)
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
              layout = applyLayout(image)
              renderingContext.updateFlow.triggerUpdate()
            }
          }
        } else {
          isDone = true
          image = createImageResource()
          layout = applyLayout(image)
        }
      }
    }
  }

  private fun createImageResource(): ImageResource {
    val provider = renderingContext.imageResourceProvider as? DefaultImageResourceProvider
    if (provider == null) error("Swing Icon fallback is only supported with DefaultImageResourceProvider")
    return provider.fromSwingIcon(layer.legacyIcon, layer.generateImageModifiers(renderingContext))
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