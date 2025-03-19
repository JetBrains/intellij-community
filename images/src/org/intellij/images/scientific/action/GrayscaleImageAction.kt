// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.writeBytes
import org.intellij.images.editor.ImageDocument.IMAGE_DOCUMENT_DATA_KEY
import org.intellij.images.scientific.ScientificUtils.DEFAULT_IMAGE_FORMAT
import org.intellij.images.scientific.ScientificUtils.ORIGINAL_IMAGE_KEY
import org.intellij.images.scientific.statistics.ScientificImageActionsCollector
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class GrayscaleImageAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val originalImage = imageFile.getUserData(ORIGINAL_IMAGE_KEY) ?: return
    val grayscaleImage = applyGrayscale(originalImage)
    val byteArrayOutputStream = ByteArrayOutputStream()
    ImageIO.write(grayscaleImage, DEFAULT_IMAGE_FORMAT, byteArrayOutputStream)
    imageFile.writeBytes(byteArrayOutputStream.toByteArray())
    val document = e.getData(IMAGE_DOCUMENT_DATA_KEY) ?: return
    document.value = grayscaleImage
    ScientificImageActionsCollector.logGrayscaleImageInvoked(this)
  }

  private fun applyGrayscale(image: BufferedImage): BufferedImage {
    val grayscaleImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
    val graphics = grayscaleImage.createGraphics()
    graphics.drawImage(image, 0, 0, null)
    graphics.dispose()
    return grayscaleImage
  }
}