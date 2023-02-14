// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.icon

import com.intellij.ui.AsyncImageIcon
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.ImageUtil.ensureHiDPI
import com.intellij.util.ui.ImageUtil.scaleImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.awt.Image
import javax.swing.Icon

class AsyncImageIconsProvider<T : Any>(
  private val scope: CoroutineScope,
  private val loader: AsyncImageLoader<T>
) : IconsProvider<T> {

  override fun getIcon(key: T?, iconSize: Int): Icon {
    val baseIcon = loader.createBaseIcon(key, iconSize)
    return if (key == null) {
      baseIcon
    }
    else {
      AsyncImageIcon(scope, baseIcon) { scaleCtx, width, height ->
        loadAndResizeImage(key, scaleCtx, width, height)
      }
    }
  }

  private suspend fun loadAndResizeImage(key: T, scaleCtx: ScaleContext, width: Int, height: Int): Image? =
    withContext(Dispatchers.IO) {
      loader.load(key)?.let { image ->
        withContext(RESIZE_DISPATCHER) {
          val hidpiImage = ensureHiDPI(image, scaleCtx)
          val scaleImage = scaleImage(hidpiImage, width, height)
          loader.postProcess(scaleImage)
        }
      }
    }

  interface AsyncImageLoader<T : Any> {
    suspend fun load(key: T): Image?
    fun createBaseIcon(key: T?, iconSize: Int): Icon = EmptyIcon.create(iconSize)
    suspend fun postProcess(image: Image): Image = image
  }

  companion object {
    private val RESIZE_DISPATCHER = AppExecutorUtil.createBoundedApplicationPoolExecutor(
      "Collaboration Tools images resizing executor",
      3
    ).asCoroutineDispatcher()
  }
}