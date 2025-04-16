// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.writeBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.images.editor.ImageDocument.IMAGE_DOCUMENT_DATA_KEY
import org.intellij.images.scientific.statistics.ScientificImageActionsCollector
import org.intellij.images.scientific.utils.ScientificUtils
import org.intellij.images.scientific.utils.ScientificUtils.DEFAULT_IMAGE_FORMAT
import org.intellij.images.scientific.utils.ScientificUtils.ROTATION_ANGLE_KEY
import org.intellij.images.scientific.utils.launchBackground
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class RotateImageAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    var currentAngle = imageFile.getUserData(ROTATION_ANGLE_KEY) ?: 0
    currentAngle = (currentAngle + 90) % 360
    imageFile.putUserData(ROTATION_ANGLE_KEY, currentAngle)
    val document = e.getData(IMAGE_DOCUMENT_DATA_KEY) ?: return
    val currentImage = document.value
    launchBackground {
      val rotatedImage = ScientificUtils.rotateImage(currentImage, 90)
      saveImageToFile(imageFile, rotatedImage)
      document.value = rotatedImage
      ScientificImageActionsCollector.logRotateImageInvoked(this@RotateImageAction)
    }
  }

  private suspend fun saveImageToFile(imageFile: VirtualFile, rotatedImage: BufferedImage) = withContext(Dispatchers.IO) {
    val byteArrayOutputStream = ByteArrayOutputStream()
    ImageIO.write(rotatedImage, DEFAULT_IMAGE_FORMAT, byteArrayOutputStream)
    imageFile.writeBytes(byteArrayOutputStream.toByteArray())
  }
}