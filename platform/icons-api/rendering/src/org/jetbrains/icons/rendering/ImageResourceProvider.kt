// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.rendering

import org.jetbrains.icons.InternalIconsApi
import java.util.ServiceLoader

@InternalIconsApi
interface ImageResourceProvider {
  fun loadImage(loader: ImageResourceLoader, imageModifiers: ImageModifiers? = null): ImageResource

  companion object {
    @Volatile
    private var instance: ImageResourceProvider? = null

    @JvmStatic
    fun getInstance(): ImageResourceProvider = instance ?: loadFromSPI()

    private fun loadFromSPI(): ImageResourceProvider =
      ServiceLoader.load(ImageResourceProvider::class.java).firstOrNull()
      ?: error("ImageResourceProvider instance is not set and there is no SPI service on classpath.")

    fun activate(provider: ImageResourceProvider) {
      instance = provider
    }
  }
}