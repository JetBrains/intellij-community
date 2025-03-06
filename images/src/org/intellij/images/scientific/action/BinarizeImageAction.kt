// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.writeBytes
import org.intellij.images.ImagesBundle
import org.intellij.images.editor.ImageDocument
import org.intellij.images.scientific.ScientificUtils
import org.intellij.images.scientific.BinarizationThresholdConfig
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.JOptionPane
import javax.swing.JTextField


class BinarizeImageAction : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT


  override fun actionPerformed(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val originalImage = imageFile.getUserData(ScientificUtils.ORIGINAL_IMAGE_KEY) ?: return
    val thresholdConfig = ApplicationManager.getApplication().getService(BinarizationThresholdConfig::class.java) ?: return
    val currentThreshold = thresholdConfig.threshold
    val newThreshold = showThresholdDialog(e.project, currentThreshold)
    if (newThreshold != null) {
      thresholdConfig.threshold = newThreshold
    }
    else {
      return
    }
    val byteArrayOutputStream = ByteArrayOutputStream()
    val binarizedImage = applyBinarization(originalImage, newThreshold)
    ImageIO.write(binarizedImage, ScientificUtils.DEFAULT_IMAGE_FORMAT, byteArrayOutputStream)
    imageFile.writeBytes(byteArrayOutputStream.toByteArray())
    val document = e.getData(ImageDocument.IMAGE_DOCUMENT_DATA_KEY) ?: return
    document.value = binarizedImage
  }


  private fun applyBinarization(image: BufferedImage, threshold: Int): BufferedImage {
    val binarizedImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until image.height) {
      for (x in 0 until image.width) {
        val rgba = image.getRGB(x, y)
        val alpha = (rgba shr 24) and 0xFF
        val red = (rgba shr 16) and 0xFF
        val green = (rgba shr 8) and 0xFF
        val blue = rgba and 0xFF
        val brightness = (0.3 * red + 0.59 * green + 0.11 * blue).toInt()
        val binaryColor = if (brightness < threshold) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        val finalColor = if (alpha < 255) {
          (alpha shl 24) or (binaryColor and 0x00FFFFFF)
        }
        else {
          binaryColor
        }
        binarizedImage.setRGB(x, y, finalColor)
      }
    }
    return binarizedImage
  }


  private fun showThresholdDialog(project: com.intellij.openapi.project.Project?, initialValue: Int): Int? {
    val inputField = JTextField(initialValue.toString())
    val optionPane = JOptionPane(
      inputField,
      JOptionPane.PLAIN_MESSAGE,
      JOptionPane.OK_CANCEL_OPTION
    )
    val dialog = optionPane.createDialog(null, ImagesBundle.message("image.binarize.dialog.title"))
    dialog.isAlwaysOnTop = true
    dialog.isVisible = true
    if (optionPane.value == JOptionPane.OK_OPTION) {
      val threshold = inputField.text.toIntOrNull()
      if (threshold != null && threshold in 0..255) {
        return threshold
      }
      else {
        JOptionPane.showMessageDialog(
          null,
          ImagesBundle.message("image.binarize.dialog.message"),
          ImagesBundle.message("image.binarize.dialog.invalid"),
          JOptionPane.ERROR_MESSAGE
        )
        return showThresholdDialog(project, initialValue)
      }
    }
    return null
  }
}