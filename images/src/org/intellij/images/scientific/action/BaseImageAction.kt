// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.images.editor.ImageDocument
import org.intellij.images.scientific.utils.ImageTransformationData
import org.intellij.images.scientific.utils.ImageTransformationData.Companion.ORIGINAL_IMAGE_KEY
import org.intellij.images.scientific.utils.ScientificUtils
import org.intellij.images.scientific.utils.launchBackground
import java.awt.image.BufferedImage

abstract class BaseImageAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
    e.presentation.isEnabledAndVisible = imageFile != null &&
                                         imageFile.getUserData(ScientificUtils.SCIENTIFIC_MODE_KEY) != null &&
                                         isActionEnabled(imageFile)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val originalImage = imageFile.getUserData(ORIGINAL_IMAGE_KEY) ?: return
    val document = e.getData(ImageDocument.IMAGE_DOCUMENT_DATA_KEY) ?: return
    val currentImage = document.value ?: return
    val transformationData = ImageTransformationData.getInstance(imageFile) ?: return
    launchBackground {
      val resultImage = performImageTransformation(originalImage, currentImage, imageFile, transformationData)
      if (resultImage != null) {
        ScientificUtils.saveImageToFile(imageFile, resultImage)
        document.value = resultImage
      }
    }
  }

  protected abstract suspend fun performImageTransformation(
    originalImage: BufferedImage,
    currentImage: BufferedImage,
    imageFile: VirtualFile,
    transformationData: ImageTransformationData
  ): BufferedImage?

  protected open fun isActionEnabled(imageFile: VirtualFile): Boolean = true
}