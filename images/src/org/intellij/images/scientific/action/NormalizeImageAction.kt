// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import org.intellij.images.ImagesBundle
import org.intellij.images.editor.ImageDocument.IMAGE_DOCUMENT_DATA_KEY
import org.intellij.images.scientific.statistics.ScientificImageActionsCollector
import org.intellij.images.scientific.utils.ScientificUtils
import org.intellij.images.scientific.utils.ScientificUtils.CURRENT_NOT_NORMALIZED_IMAGE_KEY
import org.intellij.images.scientific.utils.ScientificUtils.NORMALIZATION_APPLIED_KEY
import org.intellij.images.scientific.utils.ScientificUtils.ORIGINAL_IMAGE_KEY
import org.intellij.images.scientific.utils.ScientificUtils.ROTATION_ANGLE_KEY
import org.intellij.images.scientific.utils.ScientificUtils.saveImageToFile
import org.intellij.images.scientific.utils.launchBackground

class NormalizeImageAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
    e.presentation.isEnabledAndVisible = imageFile?.getUserData(ScientificUtils.SCIENTIFIC_MODE_KEY) != null
    val normalizationApplied = imageFile?.getUserData(NORMALIZATION_APPLIED_KEY) ?: false
    e.presentation.text = if (normalizationApplied) ImagesBundle.message("action.restore.original.text") else ImagesBundle.message("action.normalize.image.text")
  }

  override fun actionPerformed(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val originalImage = imageFile.getUserData(ORIGINAL_IMAGE_KEY) ?: return
    val document = e.getData(IMAGE_DOCUMENT_DATA_KEY) ?: return
    val currentAngle = imageFile.getUserData(ROTATION_ANGLE_KEY) ?: 0
    val normalizationApplied = imageFile.getUserData(NORMALIZATION_APPLIED_KEY) ?: false

    launchBackground {
      if (!normalizationApplied) {
        imageFile.putUserData(CURRENT_NOT_NORMALIZED_IMAGE_KEY, ScientificUtils.rotateImage(document.value, 360 - currentAngle))
        val normalizedImage = ScientificUtils.normalizeImage(document.value)
        saveImageToFile(imageFile, normalizedImage)
        document.value = normalizedImage
        imageFile.putUserData(NORMALIZATION_APPLIED_KEY, true)
      } else {
        val notNormalizedImage = imageFile.getUserData(CURRENT_NOT_NORMALIZED_IMAGE_KEY) ?: originalImage
        val rotatedNotNormalizedImage = if (currentAngle != 0) ScientificUtils.rotateImage(notNormalizedImage, currentAngle) else notNormalizedImage
        saveImageToFile(imageFile, rotatedNotNormalizedImage)
        document.value = rotatedNotNormalizedImage
        imageFile.putUserData(NORMALIZATION_APPLIED_KEY, false)
      }
      ScientificImageActionsCollector.logNormalizedImageInvoked(!normalizationApplied)
    }
  }
}