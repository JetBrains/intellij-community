// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.avatar

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.ui.ScalingAsyncImageIcon
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.Image
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture
import javax.swing.Icon

abstract class CachingAvatarIconsProvider<T : Any>(private val defaultIcon: Icon) : AvatarIconsProvider<T> {

  private val iconsCache = Caffeine.newBuilder()
    .expireAfterAccess(Duration.of(5, ChronoUnit.MINUTES))
    .build<Pair<T, Int>, Icon> { (key, size) ->
      ScalingAsyncImageIcon(
        size,
        defaultIcon,
        imageLoader = {
          CompletableFuture<Image?>().completeAsync({
            loadImage(key)
          }, avatarLoadingExecutor)
        }
      )
    }

  override fun getIcon(key: T?, iconSize: Int): Icon {
    if (key == null) return IconUtil.resizeSquared(defaultIcon, iconSize)
    return iconsCache.get(key to iconSize)!!
  }

  protected abstract fun loadImage(key: T): Image?

  companion object {
    internal val avatarLoadingExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
      "Collaboration Tools avatars loading executor",
      ProcessIOExecutorService.INSTANCE,
      3
    )
  }
}