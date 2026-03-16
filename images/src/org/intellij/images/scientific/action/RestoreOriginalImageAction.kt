// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.vfs.VirtualFile
import org.intellij.images.scientific.statistics.ScientificImageActionsCollector
import org.intellij.images.scientific.utils.ImageTransformationData
import java.awt.image.BufferedImage

class RestoreOriginalImageAction : BaseImageAction() {
  override suspend fun performImageTransformation(
    originalImage: BufferedImage,
    currentImage: BufferedImage,
    imageFile: VirtualFile,
    transformationData: ImageTransformationData
  ): BufferedImage {
    transformationData.setIsNormalized(false)
    transformationData.applyTransformations(originalImage).also {
      imageFile.putUserData(CURRENT_OPERATION_MODE_KEY, ImageOperationMode.ORIGINAL_IMAGE)
      ScientificImageActionsCollector.logRestoreOriginalImageInvoked()
      return it
    }
  }
}