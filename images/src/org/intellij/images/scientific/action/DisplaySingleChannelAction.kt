// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.vfs.VirtualFile
import org.intellij.images.scientific.statistics.ScientificImageActionsCollector
import org.intellij.images.scientific.utils.ImageTransformationData
import org.intellij.images.scientific.utils.ImageTransformationData.Companion.ORIGINAL_IMAGE_KEY
import org.intellij.images.scientific.utils.ScientificUtils.displaySingleChannel
import java.awt.image.BufferedImage

class DisplaySingleChannelAction(private val channelIndex: Int) : BaseImageAction() {
  override fun isActionEnabled(imageFile: VirtualFile): Boolean {
    val originalImage = imageFile.getUserData(ORIGINAL_IMAGE_KEY)
    return originalImage != null && getDisplayableChannels(originalImage) > 1
  }

  override suspend fun performImageTransformation(
    originalImage: BufferedImage,
    currentImage: BufferedImage,
    imageFile: VirtualFile,
    transformationData: ImageTransformationData,
  ): BufferedImage {
    transformationData.setIsNormalized(false)
    val transformedImage = transformationData.applyTransformations(originalImage)
    displaySingleChannel(transformedImage, channelIndex).also {
      ScientificImageActionsCollector.logChannelSelection(channelIndex)
      return it
    }
  }

  private fun getDisplayableChannels(image: BufferedImage): Int {
    val totalChannels = image.raster.numBands
    return if (image.colorModel.hasAlpha()) totalChannels - 1 else totalChannels
  }
}