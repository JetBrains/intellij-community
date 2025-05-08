// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.images.editor.ImageDocument.IMAGE_DOCUMENT_DATA_KEY
import org.intellij.images.scientific.statistics.ScientificImageActionsCollector
import org.intellij.images.scientific.utils.ScientificUtils
import org.intellij.images.scientific.utils.ScientificUtils.IS_NORMALIZED_KEY
import org.intellij.images.scientific.utils.ScientificUtils.ORIGINAL_IMAGE_KEY
import org.intellij.images.scientific.utils.ScientificUtils.ROTATION_ANGLE_KEY
import org.intellij.images.scientific.utils.ScientificUtils.saveImageToFile
import org.intellij.images.scientific.utils.launchBackground
import java.awt.image.BufferedImage

class ReverseChannelsOrderAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val originalImage = imageFile.getUserData(ORIGINAL_IMAGE_KEY) ?: return
    val document = e.getData(IMAGE_DOCUMENT_DATA_KEY) ?: return
    val currentAngle = imageFile.getUserData(ROTATION_ANGLE_KEY) ?: 0
    imageFile.putUserData(IS_NORMALIZED_KEY, false)


    launchBackground {
      val rotatedOriginal = if (currentAngle != 0) ScientificUtils.rotateImage(originalImage, currentAngle) else originalImage
      val reversedImage = applyReverseChannelsOrder(rotatedOriginal)
      saveImageToFile(imageFile, reversedImage)
      document.value = reversedImage
      ScientificImageActionsCollector.logReverseChannelsOrderInvoked()
    }
  }

  private suspend fun applyReverseChannelsOrder(image: BufferedImage): BufferedImage = withContext(Dispatchers.IO) {
    val reversedImage = BufferedImage(image.width, image.height, image.type)
    for (x in 0 until image.width) {
      for (y in 0 until image.height) {
        val rgba = image.getRGB(x, y)
        val alpha = (rgba ushr 24) and 0xFF
        val red = (rgba ushr 16) and 0xFF
        val green = (rgba ushr 8) and 0xFF
        val blue = rgba and 0xFF
        val reversedRgba = (alpha shl 24) or (blue shl 16) or (green shl 8) or red
        reversedImage.setRGB(x, y, reversedRgba)
      }
    }

    reversedImage
  }
}