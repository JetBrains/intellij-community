// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import org.intellij.images.fileTypes.ImageFileTypeManager
import java.awt.Desktop
import java.io.IOException

/**
 * Open image file externally.
 *
 * @author [Alexey Efimov](mailto:aefimov.box@gmail.com)
 * @author Konstantin Bulenkov
 */
class EditExternallyAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val imageFile = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE)
    try {
      Desktop.getDesktop().open(imageFile.toNioPath().toFile())
    } catch (ignore: IOException) {}
  }

  override fun update(e: AnActionEvent) {
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    val enabled = file != null && ImageFileTypeManager.getInstance().isImage(file)
    if (e.place == ActionPlaces.PROJECT_VIEW_POPUP) {
      e.presentation.isVisible = enabled
    }

    e.presentation.isEnabled = enabled
  }
}