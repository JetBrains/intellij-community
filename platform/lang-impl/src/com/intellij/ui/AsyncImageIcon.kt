// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.ScalableIcon
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.icons.CopyableIcon
import com.intellij.ui.paint.withTxAndClipAligned
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleContextCache
import com.intellij.util.IconUtil
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Graphics
import java.awt.Image
import java.awt.Rectangle
import javax.swing.Icon

/**
 * Provide a way to show [Image] loaded in background as [Icon]
 * Icon size will be taken from placeholder [defaultIcon] and should be loaded and scaled to size with [imageLoader]
 * This implementation takes all scales into account
 *
 * @see [ScalingDeferredSquareImageIcon]
 * if it is better for the case to use single thread shared with all [DeferredIconImpl] instances to load the [Image]
 *
 */
@ApiStatus.Internal
@ApiStatus.Experimental
class AsyncImageIcon private constructor(
  parentCs: CoroutineScope,
  defaultIcon: Icon,
  private val scale: Float = 1.0f,
  // allows keeping cache after scale and copy functions
  cache: ScaleContextCache<ImageRequest>?,
  private val imageLoader: suspend (ScaleContext, Int, Int) -> Image?
) : Icon, ScalableIcon, CopyableIcon {

  private val cs = parentCs.childScope(Dispatchers.Main)

  constructor(
    scope: CoroutineScope,
    defaultIcon: Icon,
    scale: Float = 1.0f,
    imageLoader: suspend (ScaleContext, Int, Int) -> Image?
  ) : this(scope, defaultIcon, scale, null, imageLoader)

  private val defaultIcon = IconUtil.scale(defaultIcon, null, scale)

  private val repaintScheduler = RepaintScheduler()

  // Icon can be located on different monitors (with different ScaleContext),
  // so it is better to cache image for each
  private val imageRequestsCache = cache ?: ScaleContextCache(::requestImage)

  private fun requestImage(scaleCtx: ScaleContext): ImageRequest {
    val request = ImageRequest()
    cs.launch {
      try {
        request.image = imageLoader(scaleCtx, iconWidth, iconHeight)
      }
      catch (e: Throwable) {
        if (e is ProcessCanceledException || e is CancellationException) {
          imageRequestsCache.clear()
        }
        else {
          LOG.debug("Image loading failed", e)
        }
      }
      finally {
        request.completed = true
        repaintScheduler.scheduleRepaint(iconWidth, iconHeight)
      }
    }
    return request
  }

  override fun getIconHeight(): Int = defaultIcon.iconHeight
  override fun getIconWidth(): Int = defaultIcon.iconWidth

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    val imageRequest = imageRequestsCache.getOrProvide(ScaleContext.create(c))!!
    if (!imageRequest.completed && c != null) {
      repaintScheduler.requestRepaint(c, x, y)
    }

    val image = imageRequest.image
    if (image == null) {
      defaultIcon.paintIcon(c, g, x, y)
    }
    else {
      val bounds = Rectangle(x, y, iconWidth, iconHeight)
      withTxAndClipAligned(g, x, y, iconWidth, iconHeight) {
        StartupUiUtil.drawImage(g, image, bounds, c)
      }
    }
  }

  override fun copy(): Icon = AsyncImageIcon(cs, defaultIcon, scale, imageRequestsCache, imageLoader)

  override fun getScale(): Float = scale

  override fun scale(scaleFactor: Float): Icon = AsyncImageIcon(cs, defaultIcon, scaleFactor, imageRequestsCache, imageLoader)

  companion object {
    private val LOG = logger<AsyncImageIcon>()
  }
}

private class ImageRequest {
  var completed: Boolean = false
  var image: Image? = null
}

private class RepaintScheduler {
  // We collect repaintRequests for the icon to understand which Components should be repainted when icon is loaded
  // We can receive paintIcon few times for the same component but with different x, y
  // Only the last request should be scheduled
  private val repaintRequests = mutableMapOf<Component, RepaintRequest>()

  fun requestRepaint(c: Component, x: Int, y: Int) {
    repaintRequests[c] = repaintScheduler.createRepaintRequest(c, x, y)
  }

  fun scheduleRepaint(width: Int, height: Int) {
    for ((_, repaintRequest) in repaintRequests) {
      repaintScheduler.scheduleRepaint(repaintRequest, width, height, alwaysSchedule = false)
    }
    repaintRequests.clear()
  }

  companion object {
    // Scheduler for all DelegatingIcon instances. It repaints components that contain these icons in a batch
    private val repaintScheduler = DeferredIconRepaintScheduler()
  }
}