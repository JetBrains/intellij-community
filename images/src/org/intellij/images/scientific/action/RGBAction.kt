// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import org.intellij.images.editor.ImageEditor
import org.intellij.images.scientific.ScientificUtils
import org.intellij.images.ui.ImageComponent

class RGBAction : DumbAwareAction() {

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
    document.setValue(originalImage) // TODO: restore original image!
  }

  private fun getImageEditor(e: AnActionEvent): ImageEditor? {
    return null
  }

  private fun getImageComponent(imageEditor: ImageEditor): ImageComponent? {
    return null
  }
}