// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.images.ImagesBundle
import org.intellij.images.scientific.statistics.ScientificImageActionsCollector
import org.intellij.images.scientific.utils.ImageTransformationData
import org.intellij.images.scientific.utils.ImageTransformationData.Companion.CURRENT_ANGLE_KEY
import org.intellij.images.scientific.utils.ImageTransformationData.Companion.CURRENT_NOT_NORMALIZED_IMAGE_KEY
import org.intellij.images.scientific.utils.ImageTransformationData.Companion.ORIGINAL_IMAGE_KEY
import org.intellij.images.scientific.utils.ScientificUtils
import java.awt.image.BufferedImage

class NormalizeImageAction : BaseImageAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val transformationData = ImageTransformationData.getInstance(imageFile) ?: return
    val normalizationApplied = transformationData.isNormalized()
    e.presentation.text = if (normalizationApplied) ImagesBundle.message("action.restore.original.text") else ImagesBundle.message("action.normalize.image.text")
  }

  override suspend fun performImageTransformation(
    originalImage: BufferedImage,
    currentImage: BufferedImage,
    imageFile: VirtualFile,
    transformationData: ImageTransformationData
  ): BufferedImage? {
    val normalizationApplied = transformationData.isNormalized()
    val originalImage = imageFile.getUserData(ORIGINAL_IMAGE_KEY) ?: return null

    return if (!normalizationApplied) {
      imageFile.putUserData(CURRENT_NOT_NORMALIZED_IMAGE_KEY, currentImage)
      imageFile.putUserData(CURRENT_ANGLE_KEY, transformationData.getRotationAngle())
      val normalizedImage = ScientificUtils.normalizeImage(currentImage)
      transformationData.setIsNormalized(true)
      ScientificImageActionsCollector.logNormalizedImageInvoked(true)
      normalizedImage
    } else {
      var notNormalizedImage = imageFile.getUserData(CURRENT_NOT_NORMALIZED_IMAGE_KEY) ?: originalImage
      val currentAngle = imageFile.getUserData(CURRENT_ANGLE_KEY) ?: 0
      notNormalizedImage = ScientificUtils.rotateImage(notNormalizedImage, transformationData.getRotationAngle() - currentAngle)
      transformationData.setIsNormalized(false)
      ScientificImageActionsCollector.logNormalizedImageInvoked(false)
      notNormalizedImage
    }
  }
}