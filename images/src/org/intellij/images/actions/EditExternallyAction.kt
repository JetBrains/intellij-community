// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.actions

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.EnvironmentUtil
import org.intellij.images.ImagesBundle
import org.intellij.images.fileTypes.ImageFileTypeManager
import org.intellij.images.options.impl.ImagesConfigurable
import java.awt.Desktop
import java.io.File
import java.io.IOException

/**
 * Open image file externally.
 *
 * @author [Alexey Efimov](mailto:aefimov.box@gmail.com)
 * @author Konstantin Bulenkov
 */
internal class EditExternallyAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val imageFile = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE)
    var executablePath = PropertiesComponent.getInstance().getValue(EditExternalImageEditorAction.EXT_PATH_KEY, "")
    if (!StringUtil.isEmpty(executablePath)) {
      EnvironmentUtil.getEnvironmentMap().forEach { (varName, varValue) ->
        executablePath = if (SystemInfo.isWindows) StringUtil.replace(executablePath, "%$varName%", varValue, true)
                         else StringUtil.replace(executablePath, "\${$varName}", varValue, false);
        }

      executablePath = FileUtil.toSystemDependentName(executablePath);
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
        Messages.showErrorDialog(e.project, ex.localizedMessage, ImagesBundle.message("error.title.launching.external.editor"));
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

  override fun update(e: AnActionEvent) {
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    val enabled = file != null && ImageFileTypeManager.getInstance().isImage(file)
    if (ActionPlaces.isPopupPlace(e.place)) {
      e.presentation.isVisible = enabled
    }

    e.presentation.isEnabled = enabled
  }
}