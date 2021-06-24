// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.avatar

import com.google.common.cache.CacheBuilder
import com.intellij.ui.ScalingDeferredSquareImageIcon
import com.intellij.util.IconUtil
import java.awt.Image
import java.util.concurrent.TimeUnit
import javax.swing.Icon

abstract class CachingAvatarIconsProvider<T>(private val defaultIcon: Icon) : AvatarIconsProvider<T> {

  private val iconsCache = CacheBuilder.newBuilder()
    .expireAfterAccess(5, TimeUnit.MINUTES)
    .build<Pair<T, Int>, Icon>()

  override fun getIcon(key: T?, iconSize: Int): Icon {
    if (key == null) return IconUtil.resizeSquared(defaultIcon, iconSize)
    return iconsCache.get(key to iconSize) {
      ScalingDeferredSquareImageIcon(iconSize, defaultIcon, key, ::loadImage)
    }
  }

  protected abstract fun loadImage(key: T): Image?
}