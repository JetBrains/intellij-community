// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ImportSettingsFilenameFilter
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.startup.StartupActionScriptManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.showOkCancelDialog
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtilRt
import com.intellij.util.io.copy
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString

open class ImportSettingsAction : AnAction(), ActionRemoteBehaviorSpecification.Frontend, DumbAware {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = true
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val dataContext = e.dataContext
    val component = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext)

    val descriptor = object : FileChooserDescriptor(true, true, true, true, false, false) {
      override fun isFileSelectable(file: VirtualFile?): Boolean {
        if (file?.isDirectory == true) {
          return file.fileSystem.getNioPath(file)?.let { path -> ConfigImportHelper.isConfigDirectory(path) } == true
        }
        return super.isFileSelectable(file)
      }
    }.apply {
      title = ConfigurationStoreBundle.message("title.import.file.location")
      description = ConfigurationStoreBundle.message("prompt.choose.import.file.path")
      isHideIgnored = false
      withFileFilter { ConfigImportHelper.isSettingsFile(it) }
    }

    chooseSettingsFile(descriptor, PathManager.getOriginalConfigDir().pathString, component) {
      val saveFile = Paths.get(it.path)
      try {
        doImport(saveFile)
      }
      catch (e1: ZipException) {
        Messages.showErrorDialog(
          ConfigurationStoreBundle.message("error.reading.settings.file", saveFile, e1.message),
          ConfigurationStoreBundle.message("title.invalid.file"))
      }
      catch (e1: IOException) {
          Messages.showErrorDialog(ConfigurationStoreBundle.message("error.reading.settings.file.2", saveFile, e1.message),
                                   IdeBundle.message("title.error.reading.file"))
        }
      }
  }

  protected open fun getExportableComponents(relativePaths: Set<String>): Map<FileSpec, List<ExportableItem>> {
    return getExportableComponentsMap(isComputePresentableNames = true, withDeprecated = true)
      .filterKeys { relativePaths.contains(it.relativePath) }
  }

  protected open fun getMarkedComponents(components: Set<ExportableItem>): Set<ExportableItem> = components

  protected open fun doImport(saveFile: Path) {
    if (!saveFile.exists()) {
      Messages.showErrorDialog(ConfigurationStoreBundle.message("error.cannot.find.file", saveFile),
                               ConfigurationStoreBundle.message("title.file.not.found"))
      return
    }

    if (saveFile.isDirectory()) {
      doImportFromDirectory(saveFile)
      return
    }

    val relativePaths = getPaths(saveFile.inputStream())
    if (!relativePaths.contains(ImportSettingsFilenameFilter.SETTINGS_JAR_MARKER)) {
      Messages.showErrorDialog(
        ConfigurationStoreBundle.message("error.no.settings.to.import", saveFile),
        ConfigurationStoreBundle.message("title.invalid.file"))
      return
    }

    val configPath = PathManager.getOriginalConfigDir()
    val dialog = ChooseComponentsToExportDialog(
      getExportableComponents(relativePaths), false,
      ConfigurationStoreBundle.message("title.select.components.to.import"),
      ConfigurationStoreBundle.message("prompt.check.components.to.import"))
    if (!dialog.showAndGet()) {
      return
    }

    val tempFile = PathManager.getStartupScriptDir().resolve(saveFile.fileName)
    saveFile.copy(tempFile)
    val filenameFilter = ImportSettingsFilenameFilter(getRelativeNamesToExtract(getMarkedComponents(dialog.exportableComponents)))
    StartupActionScriptManager.addActionCommands(listOf(StartupActionScriptManager.UnzipCommand(tempFile, configPath, filenameFilter),
                                                        StartupActionScriptManager.DeleteCommand(tempFile)))

    UpdateSettings.getInstance().forceCheckForUpdateAfterRestart()

    if (confirmRestart(ConfigurationStoreBundle.message("message.settings.imported.successfully", getRestartActionName(),
                                                        ApplicationNamesInfo.getInstance().fullProductName))) {
      restart()
    }
  }

  private fun restart() {
    invokeLater {
      (ApplicationManager.getApplication() as ApplicationEx).restart(true)
    }
  }

  private fun confirmRestart(@NlsContexts.DialogMessage message: String): Boolean =
    (Messages.OK == showOkCancelDialog(
      title = ConfigurationStoreBundle.message("import.settings.confirmation.title"),
      message = message,
      okText = getRestartActionName(),
      icon = Messages.getQuestionIcon()
    ))

  @NlsContexts.Button
  private fun getRestartActionName(): String =
    if (ApplicationManager.getApplication().isRestartCapable)
      ConfigurationStoreBundle.message("import.settings.confirmation.button.restart")
    else
      ConfigurationStoreBundle.message("import.default.settings.confirmation.button.shutdown")

  private fun doImportFromDirectory(saveFile: Path) {
    val confirmationMessage = ConfigurationStoreBundle.message("restore.default.settings.confirmation.message",
                                                               ConfigBackup.getNextBackupPath(PathManager.getOriginalConfigDir()))
    if (confirmRestart(confirmationMessage)) {
      CustomConfigMigrationOption.MigrateFromCustomPlace(saveFile).writeConfigMarkerFile()
      restart()
    }
  }

  private fun getRelativeNamesToExtract(chosenComponents: Set<ExportableItem>): Set<String> {
    val result = HashSet<String>()
    for (item in chosenComponents) {
      result.add(item.fileSpec.relativePath)
    }

    result.add(PluginManager.INSTALLED_TXT)
    return result
  }
}

fun getPaths(input: InputStream): Set<String> {
  val result = mutableSetOf<String>()
  val zipIn = ZipInputStream(input)
  zipIn.use {
    while (true) {
      val entry = zipIn.nextEntry ?: break
      var path = entry.name.trimEnd('/')
      result.add(path)
      while (true) {
        path = PathUtilRt.getParentPath(path).takeIf { it.isNotEmpty() } ?: break
        result.add(path)
      }
    }
  }
  return result
}
