// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.actions

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.ui.UIBundle
import com.intellij.util.EnvironmentUtil
import com.intellij.util.io.delete
import kotlinx.coroutines.*
import org.intellij.images.ImagesBundle
import org.intellij.images.fileTypes.ImageFileTypeManager
import org.intellij.images.options.impl.ImagesConfigurable
import org.intellij.images.ui.ImageComponentDecorator
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * Open image file externally.
 *
 * @author [Alexey Efimov](mailto:aefimov.box@gmail.com)
 * @author Konstantin Bulenkov
 */
internal class EditExternallyAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    if (imageFile.toNioPathOrNull() != null) {
      actionPerformed(e, imageFile)
    }
    else {
      performActionWithBackingFile(e, imageFile)
    }
  }

  override fun update(e: AnActionEvent) {
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    val enabled = file != null && ImageFileTypeManager.getInstance().isImage(file)
    if (e.isFromContextMenu) {
      e.presentation.isVisible = enabled
    }

    e.presentation.isEnabled = enabled
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  private fun actionPerformed(e: AnActionEvent, imageFile: VirtualFile) {
    var executablePath = PropertiesComponent.getInstance().getValue(EditExternalImageEditorAction.EXT_PATH_KEY, "")
    if (!StringUtil.isEmpty(executablePath)) {
      EnvironmentUtil.getEnvironmentMap().forEach { (varName, varValue) ->
        executablePath = if (SystemInfo.isWindows) StringUtil.replace(executablePath, "%$varName%", varValue, true)
                         else StringUtil.replace(executablePath, "\${$varName}", varValue, false)
        }

      executablePath = FileUtil.toSystemDependentName(executablePath)
      val executable = File(executablePath)
      val commandLine = GeneralCommandLine()
      val path = if(executable.exists()) executable.absolutePath else executablePath
      if (SystemInfo.isMac) {
        commandLine.exePath = ExecUtil.openCommandPath
        commandLine.addParameter("-a")
        commandLine.addParameter(path)
      }
      else {
        commandLine.exePath = path
      }

      val typeManager = ImageFileTypeManager.getInstance()

      if (imageFile.isInLocalFileSystem && typeManager.isImage(imageFile)) {
        commandLine.addParameter(VfsUtilCore.virtualToIoFile(imageFile).absolutePath)
      }
      commandLine.workDirectory = File(executablePath).parentFile

      try {
        commandLine.createProcess()
      }
      catch (ex: ExecutionException) {
        Messages.showErrorDialog(e.project, ex.localizedMessage, ImagesBundle.message("error.title.launching.external.editor"))
        ImagesConfigurable.show(e.project)
      }
    }
    else {
      try {
        Desktop.getDesktop().open(imageFile.toNioPath().toFile())
      }
      catch (ignore: IOException) {
      }
    }
  }

  // Try to create a temporary backing file for the external editor to use
  private fun performActionWithBackingFile(e: AnActionEvent, imageFile: VirtualFile) {
    try {
      val disposable = e.getDisposable()
      currentThreadCoroutineScope().launch {
        try {
          val backingFile = imageFile.copyToBackingFile(disposable)
          actionPerformed(e, backingFile)
        }
        catch (e: IllegalStateException) {
          thisLogger().warn("Failed to open external image editor", e)
          withContext(Dispatchers.EDT) {
            Messages.showErrorDialog(ImagesBundle.message("error.cannot.edit.image.external.editor"), UIBundle.message("error.dialog.title"))
          }
        }
      }
    }
    catch (e: IllegalStateException) {
      thisLogger().warn("Failed to open external image editor", e)
      Messages.showErrorDialog(ImagesBundle.message("error.cannot.edit.image.external.editor"), UIBundle.message("error.dialog.title"))
    }
  }
}

private fun VirtualFile.copyToBackingFile(disposable: Disposable): VirtualFile {
  val filePath = Files.createTempFile("EditExternallyAction (copy)", name)
  Disposer.register(disposable, Disposable {
    filePath.safeDelete()
  })
  inputStream.use { inputStream ->
    try {
      Files.copy(inputStream, filePath, REPLACE_EXISTING)
    }
    catch (e: IOException) {
      filePath.safeDelete()
      throw IllegalStateException("Failed to create backing file", e)
    }
  }
  return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath) ?: throw IllegalStateException("Failed to create virtual file")
}

private fun Path.safeDelete() {
  try {
    delete()
  }
  catch (ignore: IOException) {
  }
}

/**
 * Tries to get a Disposable from the event
 *
 * If `ImageComponentDecorator.DATA_KEY` exists and is a `Disposable`, use it (ImageEditorUI returns a
 * [org.intellij.images.editor.ImageEditor] which is a `Disposable`)
 * Otherwise, use PlatformCoreDataKeys.FILE_EDITOR.
 */
private fun AnActionEvent.getDisposable(): Disposable {
  val data = getData(ImageComponentDecorator.DATA_KEY)
  if (data is Disposable) {
    return data
  }
  return getData(PlatformCoreDataKeys.FILE_EDITOR)
         ?: throw IllegalStateException("Component does not provide a Disposable object")
}
