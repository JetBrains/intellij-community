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
import com.intellij.platform.icons.impl.rendering.layers.compoundSize
import com.intellij.platform.icons.impl.intellij.rendering.images.cachedImageIcon
import com.intellij.platform.icons.impl.intellij.rendering.images.legacySize
import com.intellij.ui.scale.JBUIScale
import com.intellij.platform.icons.impl.intellij.rendering.images.measuredIn
import com.intellij.platform.icons.design.dp
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

  /** The IDE zoom of [layout]. A legacy icon that is no `CachedImageIcon` can change its logical size with the zoom. */
  private var layoutZoom = JBUIScale.scale(1f)

  override var layout: LayerLayout = legacyLayout()
    get() {
      refreshForZoom()
      return field
    }

  /**
   * Reads the size of a legacy icon again when the IDE zoom changed. The design size of a `CachedImageIcon` does not
   * depend on the zoom, so its image and its layout are kept, together with the size variants that the image found.
   */
  private fun refreshForZoom() {
    val zoom = JBUIScale.scale(1f)
    if (zoom == layoutZoom) return
    layoutZoom = zoom
    if (layer.legacyIcon.cachedImageIcon() != null) return
    image = createImageResource()
    layout = legacyLayout()
  }

  /**
   * The layout of the legacy icon at its size, see [legacySize]. The renderer applies the IDE zoom to a design size. A
   * logical size already contains the zoom, so the renderer takes the zoom of its context out of it: an icon of a fixed
   * odd size keeps that size. It is the size of the artwork that the image modifiers select, as the image paints it.
   */
  private fun legacyLayout(): LayerLayout {
    val size = legacySize(layer.legacyIcon.measuredIn(layer.generateImageModifiers(renderingContext)))
    if (size.width <= 0 || size.height <= 0) return applyLayout(image)
    val width = size.width.dp.compoundSize()
    val height = size.height.dp.compoundSize()
    if (size.isLogical) return applyLayout(layer.modifier, width.withoutZoom(), height.withoutZoom())
    return applyLayout(layer.modifier, width, height)
  }
  private val isPending = AtomicBoolean(false)

  override fun render(paintingContext: LayerPaintingContext) {
    checkForUpdatesIfNeeded()
    refreshForZoom()
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
              layout = legacyLayout()
              renderingContext.updateFlow.triggerUpdate()
            }
          }
        } else {
          isDone = true
          image = createImageResource()
          layout = legacyLayout()
          // the icon was measured with the placeholder, so it is measured again
          renderingContext.updateFlow.triggerUpdate()
        }
      }
    }
  }

  private fun createImageResource(): ImageResource {
    val provider = renderingContext.imageResourceProvider as? DefaultImageResourceProvider
    if (provider == null) error("Swing Icon fallback is only supported with DefaultImageResourceProvider")
    return provider.fromSwingIcon(layer.legacyIcon, layer.generateImageModifiers(renderingContext), renderingContext.theme)
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