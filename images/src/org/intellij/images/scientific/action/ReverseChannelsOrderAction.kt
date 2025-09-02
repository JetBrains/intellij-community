// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.vfs.VirtualFile
import org.intellij.images.scientific.statistics.ScientificImageActionsCollector
import org.intellij.images.scientific.utils.ImageTransformationData
import org.intellij.images.scientific.utils.ScientificUtils.applyReverseChannelsOrder
import java.awt.image.BufferedImage

class ReverseChannelsOrderAction : BaseImageAction() {
  override suspend fun performImageTransformation(
    originalImage: BufferedImage,
    currentImage: BufferedImage,
    imageFile: VirtualFile,
    transformationData: ImageTransformationData
  ): BufferedImage {
    transformationData.setIsNormalized(false)
    val transformedImage = transformationData.applyTransformations(originalImage)
    applyReverseChannelsOrder(transformedImage).also {
      ScientificImageActionsCollector.logReverseChannelsOrderInvoked()
      return it
    }
  }
}