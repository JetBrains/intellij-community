// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.ScalableIcon
import com.intellij.ui.icons.CopyableIcon
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.UserScaleContext
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Graphics
import java.awt.Image
import java.awt.Rectangle
import java.util.concurrent.CompletableFuture
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
@ApiStatus.Experimental
class AsyncImageIcon private constructor(
  defaultIcon: Icon,
  private val scale: Float = 1.0f,
  private val imageLoader: (ScaleContext, Int, Int) -> CompletableFuture<Image?>,
  // allows keeping cache after scale and copy functions
  cache: UserScaleContext.Cache<CompletableFuture<Image?>, ScaleContext>?
) : Icon, ScalableIcon, CopyableIcon {

  constructor(
    defaultIcon: Icon,
    scale: Float = 1.0f,
    imageLoader: (ScaleContext, Int, Int) -> CompletableFuture<Image?>,
  ) : this(defaultIcon, scale, imageLoader, null)

  private val defaultIcon = IconUtil.scale(defaultIcon, null, scale)

  private val repaintScheduler = RepaintScheduler()

  // Icon can be located on different monitors (with different ScaleContext),
  // so it is better to cache image for each
  private val imageRequestsCache: UserScaleContext.Cache<CompletableFuture<Image?>, ScaleContext> =
    cache ?: ScaleContext.Cache { scaleCtx ->
      imageLoader(scaleCtx, iconWidth, iconHeight).also {
        it.thenRunAsync({ repaintScheduler.scheduleRepaint(iconWidth, iconHeight) }, EdtExecutorService.getInstance())
      }
    }

  override fun getIconHeight() = defaultIcon.iconHeight
  override fun getIconWidth() = defaultIcon.iconWidth

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    val imageRequest = imageRequestsCache.getOrProvide(ScaleContext.create(c))!!
    if (!imageRequest.isDone && c != null) {
      repaintScheduler.requestRepaint(c, x, y)
    }

    val image = try {
      imageRequest.getNow(null)
    }
    catch (error: Throwable) {
      LOG.debug("Image loading failed", error)
      null
    }

    if (image == null) {
      defaultIcon.paintIcon(c, g, x, y)
    }
    else {
      val bounds = Rectangle(x, y, iconWidth, iconHeight)
      StartupUiUtil.drawImage(g, image, bounds, c)
    }
  }

  override fun copy(): Icon = AsyncImageIcon(defaultIcon, scale, imageLoader, imageRequestsCache)

  override fun getScale(): Float = scale

  override fun scale(scaleFactor: Float): Icon = AsyncImageIcon(defaultIcon, scaleFactor, imageLoader, imageRequestsCache)

  companion object {
    private val LOG = logger<AsyncImageIcon>()
  }
}

private class RepaintScheduler {
  // We collect repaintRequests for the icon to understand which Components should be repainted when icon is loaded
  // We can receive paintIcon few times for the same component but with different x, y
  // Only the last request should be scheduled
  private val repaintRequests = mutableMapOf<Component, DeferredIconRepaintScheduler.RepaintRequest>()

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