// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.vfs.VirtualFile
import org.intellij.images.scientific.BinarizationThresholdConfig
import org.intellij.images.scientific.statistics.ScientificImageActionsCollector
import org.intellij.images.scientific.utils.ImageTransformationData
import org.intellij.images.scientific.utils.ScientificUtils.applyBinarization
import java.awt.image.BufferedImage

class BinarizeImageAction : BaseImageAction() {
  override suspend fun performImageTransformation(
    originalImage: BufferedImage,
    currentImage: BufferedImage,
    imageFile: VirtualFile,
    transformationData: ImageTransformationData
  ): BufferedImage? {
    val binarizationThreshold = BinarizationThresholdConfig.getInstance().threshold
    transformationData.setIsNormalized(false)
    val transformedImage = transformationData.applyTransformations(originalImage)
    applyBinarization(transformedImage, binarizationThreshold).also {
      ScientificImageActionsCollector.logBinarizeImageInvoked(binarizationThreshold)
      return it
    }
  }
}