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

class InvertChannelsAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val originalImage = imageFile.getUserData(ORIGINAL_IMAGE_KEY) ?: return
    val document = e.getData(IMAGE_DOCUMENT_DATA_KEY) ?: return
    val currentAngle = imageFile.getUserData(ROTATION_ANGLE_KEY) ?: 0

    launchBackground {
      val rotatedOriginal = if (currentAngle != 0) ScientificUtils.rotateImage(originalImage, currentAngle) else originalImage
      val invertedImage = applyInvertChannels(rotatedOriginal)
      saveImageToFile(imageFile, invertedImage)
      document.value = invertedImage
      ScientificImageActionsCollector.logInvertChannelsInvoked(this@InvertChannelsAction)
    }
  }

  private suspend fun applyInvertChannels(image: BufferedImage): BufferedImage = withContext(Dispatchers.IO) {
    val hasAlpha = image.colorModel.hasAlpha()
    val invertedImage = BufferedImage(image.width, image.height, if (hasAlpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
    for (x in 0 until image.width) {
      for (y in 0 until image.height) {
        val rgba = image.getRGB(x, y)
        val alpha = (rgba shr 24) and 0xFF
        val red = (rgba shr 16) and 0xFF
        val green = (rgba shr 8) and 0xFF
        val blue = rgba and 0xFF
        val invertedRed = 255 - red
        val invertedGreen = 255 - green
        val invertedBlue = 255 - blue
        val invertedRgba = (alpha shl 24) or (invertedRed shl 16) or (invertedGreen shl 8) or invertedBlue
        invertedImage.setRGB(x, y, invertedRgba)
      }
    }
    invertedImage
  }

  private suspend fun saveImageToFile(imageFile: VirtualFile, invertedImage: BufferedImage) = withContext(Dispatchers.IO) {
    val byteArrayOutputStream = ByteArrayOutputStream()
    ImageIO.write(invertedImage, DEFAULT_IMAGE_FORMAT, byteArrayOutputStream)
    imageFile.writeBytes(byteArrayOutputStream.toByteArray())
  }
}