// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.EdtExecutorService
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Graphics
import java.awt.Image
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
class AsyncImageIcon(
  private val defaultIcon: Icon,
  imageLoader: (ScaleContext, Int, Int) -> CompletableFuture<Image?>
) : Icon {

  // Icon can be located on different monitors (with different ScaleContext),
  // so it is better to cache icon for each
  private val imageIconCache = ScaleContext.Cache { scaleCtx ->
    val imageIcon = imageLoader(scaleCtx, defaultIcon.iconWidth, defaultIcon.iconHeight)
      .thenApply { image ->
        image?.let {
          IconUtil.createImageIcon(it)
        }
      }

    DelegatingIcon(defaultIcon, imageIcon)
  }

  override fun getIconHeight() = defaultIcon.iconHeight
  override fun getIconWidth() = defaultIcon.iconWidth

  override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
    imageIconCache.getOrProvide(ScaleContext.create(c))?.paintIcon(c, g, x, y)
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