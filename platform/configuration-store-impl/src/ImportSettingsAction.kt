// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

// for Rider purpose
open class ImportSettingsAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val dataContext = e.dataContext
    val component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext)
    ChooseComponentsToExportDialog.chooseSettingsFile(PathManager.getConfigPath(), component, IdeBundle.message("title.import.file.location"), IdeBundle.message("prompt.choose.import.file.path"))
      .onSuccess {
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

  protected open fun getExportableComponents(relativePaths: Set<String>): Map<Path, List<ExportableItem>> = getExportableComponentsMap(false, true, onlyPaths = relativePaths)
  protected open fun getMarkedComponents(components: Set<ExportableItem>): Set<ExportableItem> = components

  protected open fun doImport(saveFile: File) {
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
        getExportableComponents(relativePaths), false,
        IdeBundle.message("title.select.components.to.import"),
        IdeBundle.message("prompt.check.components.to.import"))
    if (!dialog.showAndGet()) {
      return
    }

    val tempFile = File(PathManager.getPluginTempPath(), saveFile.name)
    FileUtil.copy(saveFile, tempFile)
    val filenameFilter = ImportSettingsFilenameFilter(getRelativeNamesToExtract(getMarkedComponents(dialog.exportableComponents)))
    StartupActionScriptManager.addActionCommands(listOf(
      StartupActionScriptManager.UnzipCommand(tempFile, File(configPath), filenameFilter),
      StartupActionScriptManager.DeleteCommand(tempFile)))

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
    for ((file) in chosenComponents) {
      result.add(root.relativize(file).systemIndependentPath)
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
  zipIn.use {
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
  return result
}
