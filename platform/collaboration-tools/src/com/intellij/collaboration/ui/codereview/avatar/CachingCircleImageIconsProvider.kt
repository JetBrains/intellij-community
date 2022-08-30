// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.avatar

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.collaboration.async.disposingScope
import com.intellij.openapi.Disposable
import com.intellij.ui.AsyncImageIcon
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.ImageUtil.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.awt.Image
import java.time.Duration
import java.time.temporal.ChronoUnit
import javax.swing.Icon

abstract class CachingCircleImageIconsProvider<T : Any>(private val scope: CoroutineScope, private val defaultIcon: Icon)
  : IconsProvider<T> {

  private val iconsCache = Caffeine.newBuilder()
    .expireAfterAccess(Duration.of(5, ChronoUnit.MINUTES))
    .build<Pair<T?, Int>, Icon>()

  override fun getIcon(key: T?, iconSize: Int): Icon =
    iconsCache.get(key to iconSize) { (key, iconSize) ->
      val defaultIcon = IconUtil.resizeSquared(defaultIcon, iconSize)
      if (key == null) {
        defaultIcon
      }
      else {
        AsyncImageIcon(scope, defaultIcon) { scaleCtx, width, height ->
          loadAndResizeImage(key, scaleCtx, width, height)
        }
      }
    }

  private suspend fun loadAndResizeImage(key: T, scaleCtx: ScaleContext, width: Int, height: Int): Image? =
    withContext(Dispatchers.IO) {
      loadImage(key)?.let { image ->
        withContext(RESIZE_DISPATCHER) {
          val hidpiImage = ensureHiDPI(image, scaleCtx)
          val scaleImage = scaleImage(hidpiImage, width, height)
          createCircleImage(toBufferedImage(scaleImage))
        }
      }
    }

  protected abstract suspend fun loadImage(key: T): Image?

  companion object {
    private val RESIZE_DISPATCHER = AppExecutorUtil.createBoundedApplicationPoolExecutor(
      "Collaboration Tools images resizing executor",
      3
    ).asCoroutineDispatcher()
  }
}