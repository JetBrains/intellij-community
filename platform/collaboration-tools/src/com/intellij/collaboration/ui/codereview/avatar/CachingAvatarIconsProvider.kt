// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.avatar

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.ui.AsyncImageIcon
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.ImageUtil
import java.awt.Image
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture
import javax.swing.Icon

abstract class CachingAvatarIconsProvider<T : Any>(private val defaultIcon: Icon) : AvatarIconsProvider<T> {

  private val iconsCache = Caffeine.newBuilder()
    .expireAfterAccess(Duration.of(5, ChronoUnit.MINUTES))
    .build<Pair<T, Int>, Icon>()

  override fun getIcon(key: T?, iconSize: Int): Icon {
    if (key == null) {
      return IconUtil.resizeSquared(defaultIcon, iconSize)
    }

    return iconsCache.get(key to iconSize) {
      AsyncImageIcon(IconUtil.resizeSquared(defaultIcon, iconSize)) { scaleCtx, width, height ->
        CompletableFuture<Image?>().completeAsync({ loadAndResizeImage(key, scaleCtx, width, height) }, avatarLoadingExecutor)
      }
    }
  }

  private fun loadAndResizeImage(key: T, scaleCtx: ScaleContext, width: Int, height: Int): Image? {
    val image = loadImage(key) ?: return null
    return ImageUtil.resize(image, scaleCtx, width, height)
  }

  protected abstract fun loadImage(key: T): Image?

  companion object {
    private val avatarLoadingExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
      "Collaboration Tools avatars loading executor",
      ProcessIOExecutorService.INSTANCE,
      3
    )
  }
}