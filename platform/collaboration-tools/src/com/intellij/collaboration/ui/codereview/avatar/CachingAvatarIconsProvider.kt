// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.avatar

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.ui.ScalingDeferredSquareImageIcon
import com.intellij.util.IconUtil
import java.awt.Image
import java.time.Duration
import java.time.temporal.ChronoUnit
import javax.swing.Icon

abstract class CachingAvatarIconsProvider<T : Any>(private val defaultIcon: Icon) : AvatarIconsProvider<T> {

  private val iconsCache = Caffeine.newBuilder()
    .expireAfterAccess(Duration.of(5, ChronoUnit.MINUTES))
    .build<Pair<T, Int>, Icon> { (key, size) ->
      ScalingDeferredSquareImageIcon(size, defaultIcon, key, ::loadImage)
    }

  override fun getIcon(key: T?, iconSize: Int): Icon {
    if (key == null) return IconUtil.resizeSquared(defaultIcon, iconSize)
    return iconsCache.get(key to iconSize)!!
  }

  protected abstract fun loadImage(key: T): Image?
}