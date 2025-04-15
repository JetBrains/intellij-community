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
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class RestoreOriginalImageAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val originalImage = imageFile.getUserData(ORIGINAL_IMAGE_KEY) ?: return
    val byteArrayOutputStream = ByteArrayOutputStream()
    ImageIO.write(originalImage, DEFAULT_IMAGE_FORMAT, byteArrayOutputStream)
    imageFile.writeBytes(byteArrayOutputStream.toByteArray())
    val document = e.getData(IMAGE_DOCUMENT_DATA_KEY) ?: return
    document.value = originalImage
    ScientificImageActionsCollector.logRestoreOriginalImageInvoked(this)
  }
}