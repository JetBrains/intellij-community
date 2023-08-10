// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.icon

import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.TimeUnit
import javax.swing.Icon

class CachingIconsProvider<T : Any>(private val delegate: IconsProvider<T>, customizeCache: CacheCustomizer.() -> Unit = {})
  : IconsProvider<T> {

  private val iconsCache = Caffeine.newBuilder()
    .customize(customizeCache)
    .build<Pair<T?, Int>, Icon>()

  override fun getIcon(key: T?, iconSize: Int): Icon =
    iconsCache.get(key to iconSize) { (key, iconSize) ->
      delegate.getIcon(key, iconSize)
    }

  class CacheCustomizer {
    var maxSize: Int? = DEFAULT_MAX_SIZE
    var expiresAfterMinutes: Int? = 5
  }

  fun invalidateAll() {
    iconsCache.invalidateAll()
  }

  fun cleanUp() {
    iconsCache.cleanUp()
  }

  companion object {
    private const val DEFAULT_MAX_SIZE = 500

    private fun <K, V> Caffeine<K, V>.customize(customizeCache: CacheCustomizer.() -> Unit): Caffeine<K, V> {
      with(CacheCustomizer()) {
        customizeCache()

        maxSize?.let { maximumSize(it.toLong()) }
        expiresAfterMinutes?.let { expireAfterAccess(it.toLong(), TimeUnit.MINUTES) }
      }
      return this
    }
  }
}