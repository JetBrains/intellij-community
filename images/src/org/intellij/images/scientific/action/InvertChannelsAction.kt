// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import org.intellij.images.scientific.ScientificUtils
import org.intellij.images.scientific.ScientificUtils.ORIGINAL_IMAGE_KEY
import org.intellij.images.scientific.convertToByteArray
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class InvertChannelsAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
    e.presentation.isEnabledAndVisible = imageFile?.getUserData(ScientificUtils.SCIENTIFIC_MODE_KEY) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val originalImage = imageFile.getUserData(ORIGINAL_IMAGE_KEY) ?: return
    val invertedImage = applyInvertChannels(originalImage)
    imageFile.setBinaryContent(convertToByteArray(invertedImage, imageFile.fileType.defaultExtension))
  }


  private fun applyInvertChannels(image: BufferedImage): BufferedImage {
    val invertedImage = BufferedImage(image.width, image.height, image.type)
    for (x in 0 until image.width) {
      for (y in 0 until image.height) {
        val rgb = image.getRGB(x, y)
        val red = (rgb shr 16) and 0xFF
        val green = (rgb shr 8) and 0xFF
        val blue = rgb and 0xFF
        val bgr = (blue shl 16) or (green shl 8) or red
        invertedImage.setRGB(x, y, bgr)
      }
    }
    return invertedImage
  }
}