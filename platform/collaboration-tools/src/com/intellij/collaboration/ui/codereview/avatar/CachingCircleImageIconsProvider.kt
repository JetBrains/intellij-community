// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.avatar

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.ui.AsyncImageIcon
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.ImageUtil.*
import java.awt.Image
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture
import javax.swing.Icon

abstract class CachingCircleImageIconsProvider<T : Any>(private val defaultIcon: Icon) : IconsProvider<T> {

  private val iconsCache = Caffeine.newBuilder()
    .expireAfterAccess(Duration.of(5, ChronoUnit.MINUTES))
    .build<Pair<T, Int>, Icon>()

  override fun getIcon(key: T?, iconSize: Int): Icon {
    if (key == null) {
      return IconUtil.resizeSquared(defaultIcon, iconSize)
    }

    return iconsCache.get(key to iconSize) {
      AsyncImageIcon(IconUtil.resizeSquared(defaultIcon, iconSize)) { scaleCtx, width, height ->
        loadAndResizeImage(key, scaleCtx, width, height)
      }
    }
  }

  private fun loadAndResizeImage(key: T, scaleCtx: ScaleContext, width: Int, height: Int): CompletableFuture<Image?> {
    return try {
      loadImageAsync(key).thenApplyAsync({ image ->
                                           image?.let {
                                             val hidpiImage = ensureHiDPI(image, scaleCtx)
                                             val scaleImage = scaleImage(hidpiImage, width, height)
                                             createCircleImage(toBufferedImage(scaleImage))
                                           }
                                         }, avatarResizeExecutor)
    }
    catch (e: Throwable) {
      CompletableFuture.failedFuture(e)
    }
  }

  protected abstract fun loadImageAsync(key: T): CompletableFuture<Image?>

  companion object {
    private val avatarResizeExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
      "Collaboration Tools images resizing executor",
      3
    )
  }
}