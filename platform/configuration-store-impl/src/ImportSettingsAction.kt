/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.configurationStore

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ImportSettingsFilenameFilter
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.startup.StartupActionScriptManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.getParentPath
import com.intellij.util.io.systemIndependentPath
import gnu.trove.THashSet
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Paths
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

private class ImportSettingsAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val dataContext = e.dataContext
    val component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext)
    ChooseComponentsToExportDialog.chooseSettingsFile(PathManager.getConfigPath(), component, IdeBundle.message("title.import.file.location"), IdeBundle.message("prompt.choose.import.file.path"))
      .done {
        val saveFile = File(it)
        try {
          doImport(saveFile)
        }
        catch (e1: ZipException) {
          Messages.showErrorDialog(
              IdeBundle.message("error.reading.settings.file", presentableFileName(saveFile), e1.message, promptLocationMessage()),
              IdeBundle.message("title.invalid.file"))
        }
        catch (e1: IOException) {
          Messages.showErrorDialog(IdeBundle.message("error.reading.settings.file.2", presentableFileName(saveFile), e1.message),
                                   IdeBundle.message("title.error.reading.file"))
        }
      }
  }

  private fun doImport(saveFile: File) {
    if (!saveFile.exists()) {
      Messages.showErrorDialog(IdeBundle.message("error.cannot.find.file", presentableFileName(saveFile)),
                               IdeBundle.message("title.file.not.found"))
      return
    }

    val relativePaths = getPaths(saveFile.inputStream())
    if (!relativePaths.contains(ImportSettingsFilenameFilter.SETTINGS_JAR_MARKER)) {
      Messages.showErrorDialog(
          IdeBundle.message("error.file.contains.no.settings.to.import", presentableFileName(saveFile), promptLocationMessage()),
          IdeBundle.message("title.invalid.file"))
      return
    }

    val configPath = FileUtil.toSystemIndependentName(PathManager.getConfigPath())
    val dialog = ChooseComponentsToExportDialog(
        getExportableComponentsMap(false, true, onlyPaths = relativePaths), false,
        IdeBundle.message("title.select.components.to.import"),
        IdeBundle.message("prompt.check.components.to.import"))
    if (!dialog.showAndGet()) {
      return
    }

    val tempFile = File(PathManager.getPluginTempPath(), saveFile.name)
    FileUtil.copy(saveFile, tempFile)
    val filenameFilter = ImportSettingsFilenameFilter(getRelativeNamesToExtract(dialog.exportableComponents))
    StartupActionScriptManager.addActionCommand(StartupActionScriptManager.UnzipCommand(tempFile, File(configPath), filenameFilter))
    // remove temp file
    StartupActionScriptManager.addActionCommand(StartupActionScriptManager.DeleteCommand(tempFile))

    UpdateSettings.getInstance().forceCheckForUpdateAfterRestart()

    val action = IdeBundle.message(if (ApplicationManager.getApplication().isRestartCapable) "ide.restart.action" else "ide.shutdown.action")
    val message = IdeBundle.message("message.settings.imported.successfully", action, ApplicationNamesInfo.getInstance().fullProductName)
    if (Messages.showOkCancelDialog(message, IdeBundle.message("title.restart.needed"), Messages.getQuestionIcon()) == Messages.OK) {
      (ApplicationManager.getApplication() as ApplicationEx).restart(true)
    }
  }

  private fun getRelativeNamesToExtract(chosenComponents: Set<ExportableItem>): Set<String> {
    val result = THashSet<String>()
    val root = Paths.get(PathManager.getConfigPath())
    for (item in chosenComponents) {
      result.add(root.relativize(item.file).systemIndependentPath)
    }

    result.add(PluginManager.INSTALLED_TXT)
    return result
  }

  private fun presentableFileName(file: File) = "'" + FileUtil.toSystemDependentName(file.path) + "'"

  private fun promptLocationMessage() = IdeBundle.message("message.please.ensure.correct.settings")
}

fun getPaths(input: InputStream): Set<String> {
  val result = THashSet<String>()
  val zipIn = ZipInputStream(input)
  try {
    while (true) {
      val entry = zipIn.nextEntry ?: break
      var path = entry.name
      result.add(path)
      while (true) {
        path = getParentPath(path) ?: break
        result.add("$path/")
      }
    }
  }
  finally {
    zipIn.close()
  }
  return result
}
