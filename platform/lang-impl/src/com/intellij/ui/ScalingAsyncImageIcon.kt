// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.ImageUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Graphics
import java.awt.Image
import java.util.concurrent.CompletableFuture
import javax.swing.Icon

/**
 * Provide a way to show [Image] loaded in background as [Icon] with specific [size]
 * This implementation takes all scales into account
 *
 * @see [ScalingDeferredSquareImageIcon]
 * if it is better for the case to use single thread shared with all [DeferredIconImpl] instances to load the [Image]
 *
 */
@ApiStatus.Experimental
class ScalingAsyncImageIcon(
  private val size: Int,
  defaultIcon: Icon,
  imageLoader: () -> CompletableFuture<Image?>
) : Icon {
  private val baseIcon = IconUtil.resizeSquared(defaultIcon, size)

  // Icon can be located on different monitors (with different ScaleContext),
  // so it is better to cache icon for each
  private val scaledIconCache = ScaleContext.Cache { scaleCtx ->
    val imageIcon = imageLoader()
      .thenApplyAsync({ image ->
        image?.let {
          val resizedImage = ImageUtil.resize(it, size, scaleCtx)
          IconUtil.createImageIcon(resizedImage)
        }
      }, resizeExecutor)

    DelegatingIcon(baseIcon, imageIcon)
  }

  override fun getIconHeight() = baseIcon.iconHeight
  override fun getIconWidth() = baseIcon.iconWidth

  override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
    scaledIconCache.getOrProvide(ScaleContext.create(c))?.paintIcon(c, g, x, y)
  }

  companion object {
    private val resizeExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("ImageIcon resize executor", 1)
  }
}

private class DelegatingIcon(baseIcon: Icon, private val delegateResult: CompletableFuture<out Icon?>) : Icon {
  // We collect repaintRequests for the icon to understand which Components should be repainted when icon is loaded
  // We can receive paintIcon few times for the same component but with different x, y
  // Only the last request should be scheduled
  private val repaintRequests = mutableMapOf<Component, DeferredIconRepaintScheduler.RepaintRequest>()
  private var delegate: Icon = baseIcon

  init {
    delegateResult.thenApplyAsync({ icon ->
      if (icon != null) {
        delegate = icon
        for ((_, repaintRequest) in repaintRequests) {
          repaintScheduler.scheduleRepaint(repaintRequest, iconWidth, iconHeight, alwaysSchedule = false)
        }
      }
      repaintRequests.clear()
    }, EdtExecutorService.getInstance())
  }

  override fun getIconHeight() = delegate.iconHeight

  override fun getIconWidth() = delegate.iconWidth

  override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
    delegate.paintIcon(c, g, x, y)
    if (!delegateResult.isDone && c != null) {
      repaintRequests[c] = repaintScheduler.createRepaintRequest(c, x, y)
    }
  }

  companion object {
    // Scheduler for all DelegatingIcon instances. It repaints components that contain these icons in a batch
    private val repaintScheduler = DeferredIconRepaintScheduler()
  }
}