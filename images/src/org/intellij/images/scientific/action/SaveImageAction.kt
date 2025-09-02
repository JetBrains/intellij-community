// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.images.ImagesBundle
import org.intellij.images.scientific.statistics.ScientificImageActionsCollector
import org.intellij.images.scientific.utils.ScientificUtils
import org.intellij.images.scientific.utils.launchBackground
import java.io.IOException
import java.nio.file.Files
import com.intellij.openapi.diagnostic.thisLogger

internal class SaveImageAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
    if (virtualFile == null || project == null) {
      thisLogger().error("Missing project or image file.")
      return
    }

    val descriptor = FileSaverDescriptor(ImagesBundle.message("dialog.title.save.image"), "", IMAGE_FORMAT)
    val chooser = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
    val wrapper = chooser.save(project.basePath?.let { VirtualFileManager.getInstance().findFileByUrl(it) }, IMAGE_DEFAULT_NAME)

    if (wrapper == null) return
    val targetFile = wrapper.file
    val selectedFormat = targetFile.extension.lowercase()

    launchBackground {
      withContext(Dispatchers.IO) {
        try {
          Files.write(wrapper.file.toPath(), virtualFile.contentsToByteArray())
        } catch (e: IOException) {
          thisLogger().warn("Failed to save image", e)
        }
        ScientificImageActionsCollector.logSaveAsImageInvoked(selectedFormat)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
    e.presentation.isEnabledAndVisible = imageFile?.getUserData(ScientificUtils.SCIENTIFIC_MODE_KEY) != null
  }

  companion object {
    private const val IMAGE_FORMAT = "png"
    private const val IMAGE_DEFAULT_NAME: String = "myimg"
  }
}