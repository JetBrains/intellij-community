// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.writeBytes
import org.intellij.images.editor.ImageDocument.IMAGE_DOCUMENT_DATA_KEY
import org.intellij.images.scientific.utils.ScientificUtils
import org.intellij.images.scientific.utils.ScientificUtils.DEFAULT_IMAGE_FORMAT
import org.intellij.images.scientific.utils.ScientificUtils.ORIGINAL_IMAGE_KEY
import org.intellij.images.scientific.utils.ScientificUtils.ROTATION_ANGLE_KEY
import org.intellij.images.scientific.statistics.ScientificImageActionsCollector
import org.intellij.images.scientific.utils.launchBackground
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GrayscaleImageAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val originalImage = imageFile.getUserData(ORIGINAL_IMAGE_KEY) ?: return
    val document = e.getData(IMAGE_DOCUMENT_DATA_KEY) ?: return
    val currentAngle = imageFile.getUserData(ROTATION_ANGLE_KEY) ?: 0

    launchBackground {
      val rotatedOriginal = if (currentAngle != 0) ScientificUtils.rotateImage(originalImage, currentAngle) else originalImage
      val grayscaleImage = applyGrayscale(rotatedOriginal)
      saveImageToFile(imageFile, grayscaleImage)
      document.value = grayscaleImage
      ScientificImageActionsCollector.logGrayscaleImageInvoked(this@GrayscaleImageAction)
    }
  }

  private suspend fun applyGrayscale(image: BufferedImage): BufferedImage = withContext(Dispatchers.IO) {
    val grayscaleImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
    val graphics = grayscaleImage.createGraphics()
    graphics.drawImage(image, 0, 0, null)
    graphics.dispose()
    grayscaleImage
  }

  private suspend fun saveImageToFile(imageFile: VirtualFile, grayscaleImage: BufferedImage) = withContext(Dispatchers.IO) {
    val byteArrayOutputStream = ByteArrayOutputStream()
    ImageIO.write(grayscaleImage, DEFAULT_IMAGE_FORMAT, byteArrayOutputStream)
    imageFile.writeBytes(byteArrayOutputStream.toByteArray())
  }
}