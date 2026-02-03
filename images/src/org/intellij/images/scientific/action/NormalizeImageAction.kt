// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.project.DumbAwareToggleAction
import org.intellij.images.ImagesBundle
import org.intellij.images.editor.ImageDocument
import org.intellij.images.scientific.statistics.ScientificImageActionsCollector
import org.intellij.images.scientific.utils.ImageTransformationData
import org.intellij.images.scientific.utils.ImageTransformationData.Companion.CURRENT_ANGLE_KEY
import org.intellij.images.scientific.utils.ImageTransformationData.Companion.CURRENT_NOT_NORMALIZED_IMAGE_KEY
import org.intellij.images.scientific.utils.ImageTransformationData.Companion.ORIGINAL_IMAGE_KEY
import org.intellij.images.scientific.utils.ScientificUtils
import org.intellij.images.scientific.utils.launchBackground

class NormalizeImageAction : DumbAwareToggleAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
    e.presentation.isEnabledAndVisible = imageFile != null && imageFile.getUserData(ScientificUtils.SCIENTIFIC_MODE_KEY) != null
    val normalizationApplied = ImageTransformationData.getInstance(imageFile).isNormalized()
    Toggleable.setSelected(e.presentation, normalizationApplied)
    e.presentation.text = if (normalizationApplied)
      ImagesBundle.message("action.restore.original.text")
    else
      ImagesBundle.message("action.normalize.image.text")
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return false
    return ImageTransformationData.getInstance(imageFile).isNormalized()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val originalImage = imageFile.getUserData(ORIGINAL_IMAGE_KEY) ?: return
    val document = e.getData(ImageDocument.IMAGE_DOCUMENT_DATA_KEY) ?: return
    val currentImage = document.value ?: return
    val transformationData = ImageTransformationData.getInstance(imageFile)
    if (state == transformationData.isNormalized()) return

    launchBackground {
      val resultImage = if (state) {
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
      ScientificUtils.saveImageToFile(imageFile, resultImage)
      document.value = resultImage
    }
  }
}