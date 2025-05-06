// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import org.intellij.images.editor.ImageDocument.IMAGE_DOCUMENT_DATA_KEY
import org.intellij.images.scientific.statistics.ScientificImageActionsCollector
import org.intellij.images.scientific.utils.ScientificUtils
import org.intellij.images.scientific.utils.ScientificUtils.ROTATION_ANGLE_KEY
import org.intellij.images.scientific.utils.ScientificUtils.saveImageToFile
import org.intellij.images.scientific.utils.launchBackground

class RotateImageAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
    e.presentation.isEnabledAndVisible = imageFile?.getUserData(ScientificUtils.SCIENTIFIC_MODE_KEY) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    var currentAngle = imageFile.getUserData(ROTATION_ANGLE_KEY) ?: 0
    val document = e.getData(IMAGE_DOCUMENT_DATA_KEY) ?: return
    currentAngle = (currentAngle + 90) % 360
    imageFile.putUserData(ROTATION_ANGLE_KEY, currentAngle)
    val currentImage = document.value
    launchBackground {
      val rotatedImage = ScientificUtils.rotateImage(currentImage, 90)
      saveImageToFile(imageFile, rotatedImage)
      document.value = rotatedImage
      ScientificImageActionsCollector.logRotateImageInvoked(currentAngle)
    }
  }
}