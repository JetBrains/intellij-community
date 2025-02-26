// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import org.intellij.images.editor.ImageEditor
import org.intellij.images.scientific.ScientificUtils
import org.intellij.images.ui.ImageComponent
import java.awt.Color
import java.awt.image.BufferedImage

class GrayscaleAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
    e.presentation.isEnabledAndVisible = imageFile?.getUserData(ScientificUtils.SCIENTIFIC_MODE_KEY) != null
  }

  override fun actionPerformed(e: AnActionEvent) {

    val imageEditor = getImageEditor(e) ?: return
    val imageComponent = getImageComponent(imageEditor) ?: return
    val document = imageComponent.document
    val originalImage = document.value ?: return
    val grayscaleImage = applyGrayscale(originalImage)
    document.setValue(grayscaleImage) // TODO: how to view result without changing original image?
  }

  private fun getImageEditor(e: AnActionEvent): ImageEditor? {
    return null
  }

  private fun getImageComponent(imageEditor: ImageEditor): ImageComponent? {
    return null
  }

  // TODO: maybe redo logic, its just a stub!
  private fun applyGrayscale(image: BufferedImage): BufferedImage {
    val grayscaleImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
    for (x in 0 until image.width) {
      for (y in 0 until image.height) {
        val pixel = Color(image.getRGB(x, y))
        val gray = (pixel.red * 0.299 + pixel.green * 0.587 + pixel.blue * 0.114).toInt()
        val grayColor = Color(gray, gray, gray)
        grayscaleImage.setRGB(x, y, grayColor.rgb)
      }
    }
    return grayscaleImage
  }
}