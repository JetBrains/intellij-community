/*
 * Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl
import com.intellij.openapi.fileChooser.ex.FileSaverDialogImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.Label
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.notebooks.visualization.r.VisualizationBundle
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Paths
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier

object InlayOutputUtil {
  @Nls
  private val EXPORT_FAILURE_TITLE = VisualizationBundle.message("inlay.output.export.failure")
  @Nls
  private val EXPORT_FAILURE_DETAILS = VisualizationBundle.message("inlay.output.export.failure.details")
  @Nls
  private val EXPORT_FAILURE_DESCRIPTION = VisualizationBundle.message("inlay.output.export.failure.description")

  fun saveImageWithFileChooser(project: Project, image: BufferedImage, onSave: ((File) -> Unit)? = null) {
    chooseImageSaveLocation(project, image) { location ->
      ImageIO.write(image, location.extension, location)
      onSave?.invoke(location)
    }
  }

  private fun chooseImageSaveLocation(project: Project, image: BufferedImage, onChoose: (File) -> Unit) {
    val extensions = getAvailableFormats(image).toTypedArray()
    saveWithFileChooser(project, VisualizationBundle.message("inlay.output.image.export.title"), VisualizationBundle.message("inlay.output.image.export.description"), extensions, "image", false, onChoose)
  }

  private fun getAvailableFormats(image: BufferedImage): List<String> {
    val imageTypeSpecifier = ImageTypeSpecifier.createFromRenderedImage(image)
    return getAvailableFormats().filter { format ->
      imageTypeSpecifier.hasWritersFor(format)
    }
  }

  fun getAvailableFormats(): List<String> {
    return listOf("png", "jpeg", "bmp", "gif", "tiff")
  }

  private fun ImageTypeSpecifier.hasWritersFor(format: String): Boolean {
    return ImageIO.getImageWriters(this, format).asSequence().any()
  }

  fun chooseDirectory(project: Project, @Nls title: String, @Nls description: String): VirtualFile? {
    val descriptor = WritableDirectoryChooserDescriptor(title, description)
    val chooser = FileChooserDialogImpl(descriptor, project)
    val toSelect = project.virtualBaseDir.wrapInArray()
    val choice = chooser.choose(project, *toSelect)
    return choice.firstOrNull()
  }

  private fun VirtualFile?.wrapInArray(): Array<VirtualFile> {
    return if (this != null) arrayOf(this) else emptyArray()
  }

  fun saveWithFileChooser(
    project: Project,
    @NlsContexts.DialogTitle title: String,
    @Label description: String,
    extensions: Array<String>,
    defaultName: String,
    createIfMissing: Boolean,
    onChoose: (File) -> Unit
  ) {
    val descriptor = FileSaverDescriptor(title, description, *extensions)
    val chooser = FileSaverDialogImpl(descriptor, project)
    chooser.save(project.virtualBaseDir, defaultName)?.let { fileWrapper ->
      val destination = fileWrapper.file
      try {
        checkOrCreateDestinationFile(destination, createIfMissing)
        onChoose(destination)
      } catch (e: Exception) {
        notifyExportError(e)
      }
    }
  }

  private val Project.virtualBaseDir: VirtualFile?
    get() = VfsUtil.findFile(Paths.get(basePath!!), true)

  private fun checkOrCreateDestinationFile(file: File, createIfMissing: Boolean) {
    if (!file.exists()) {
      if (!file.createNewFile()) {
        throw RuntimeException(EXPORT_FAILURE_DETAILS)
      }
      if (!createIfMissing && !file.delete()) {
        throw RuntimeException(EXPORT_FAILURE_DETAILS)
      }
    }
  }

  private fun notifyExportError(e: Exception) {
    val details = e.message?.let { ":\n$it" }
    val content = "$EXPORT_FAILURE_DESCRIPTION$details"
    Messages.showErrorDialog(content, EXPORT_FAILURE_TITLE)
  }

  private class WritableDirectoryChooserDescriptor(@Nls title: String, @Nls description: String) :
    FileChooserDescriptor(false, true, false, false, false, false)
  {
    init {
      withDescription(description)
      withTitle(title)
    }

    override fun isFileSelectable(file: VirtualFile?): Boolean {
      return file?.isWritable == true && super.isFileSelectable(file)
    }
  }
}
