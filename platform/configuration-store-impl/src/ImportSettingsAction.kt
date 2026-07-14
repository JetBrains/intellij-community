// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ConfigBackup
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.CustomConfigMigrationOption
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.showOkCancelDialog
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.PathUtilRt
import com.intellij.util.io.Compressor
import com.intellij.util.io.Decompressor
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString

@Suppress("UseOptimizedEelFunctions")
@ApiStatus.Internal
open class ImportSettingsAction : AnAction(), ActionRemoteBehaviorSpecification.Frontend, DumbAware {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val dataContext = e.dataContext
    val component = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext)

    val descriptor = object : FileChooserDescriptor(FileChooserDescriptorFactory.singleFileOrDir()) {
      @Throws(Exception::class)
      override fun validateSelectedFiles(files: Array<out VirtualFile>) {
        val file = files[0]
        if (file.isDirectory && !ConfigImportHelper.isConfigDirectory(file.toNioPath())) {
          throw Exception("${file.presentableUrl} is not a valid settings directory")
        }
      }
    }.apply {
      title = ConfigurationStoreBundle.message("title.import.file.location")
      description = ConfigurationStoreBundle.message("prompt.choose.import.file.path")
      isHideIgnored = false
      ConfigImportHelper.setSettingsFilter(this)
    }

    chooseSettingsFile(descriptor, PathManager.getOriginalConfigDir().pathString, component) {
      val saveFile = it.toNioPath()
      try {
        doImport(saveFile)
      }
      catch (e1: ZipException) {
        Messages.showErrorDialog(
          ConfigurationStoreBundle.message("error.reading.settings.file", saveFile, e1.message),
          ConfigurationStoreBundle.message("title.invalid.file")
        )
      }
      catch (e1: IOException) {
        Messages.showErrorDialog(
          ConfigurationStoreBundle.message("error.reading.settings.file.2", saveFile, e1.message),
          IdeBundle.message("title.error.reading.file")
        )
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
      Messages.showErrorDialog(
        ConfigurationStoreBundle.message("error.cannot.find.file", saveFile),
        ConfigurationStoreBundle.message("title.file.not.found")
      )
      return
    }

    if (saveFile.isDirectory()) {
      doImportFromDirectory(saveFile)
      return
    }

    val relativePaths = getPaths(saveFile)
    if (!relativePaths.contains(ImportSettingsFilenameFilter.SETTINGS_JAR_MARKER)) {
      Messages.showErrorDialog(
        ConfigurationStoreBundle.message("error.no.settings.to.import", saveFile),
        ConfigurationStoreBundle.message("title.invalid.file")
      )
      return
    }

    val configPath = PathManager.getOriginalConfigDir()
    val dialog = ChooseComponentsToExportDialog(
      getExportableComponents(relativePaths), false,
      ConfigurationStoreBundle.message("title.select.components.to.import"),
      ConfigurationStoreBundle.message("prompt.check.components.to.import")
    )
    if (!dialog.showAndGet()) {
      return
    }

    val tempFile = PathManager.getStartupScriptDir().resolve(saveFile.fileName)
    runWithModalProgressBlocking(ModalTaskOwner.guess(), ConfigurationStoreBundle.message("message.settings.preparing")) {
      val filenameFilter = ImportSettingsFilenameFilter(getRelativeNamesToExtract(getMarkedComponents(dialog.exportableComponents)))
      val tempDir = Files.createTempDirectory(PathManager.getStartupScriptDir(), saveFile.fileName.toString())
      Decompressor.Zip(saveFile)
        .withZipExtensions()
        .filter(filenameFilter)
        .extract(tempDir)
      Compressor.Zip(tempFile).use { zip ->
        zip.addDirectory(tempDir)
      }
      NioFiles.deleteRecursively(tempDir)
    }

    StartupActionScriptManager.addActionCommands(listOf(
      StartupActionScriptManager.UnzipCommand(tempFile, configPath),
      StartupActionScriptManager.DeleteCommand(tempFile)
    ))

    UpdateSettings.getInstance().forceCheckForUpdateAfterRestart()

    val message = ConfigurationStoreBundle.message("message.settings.imported.successfully", getRestartActionName(), ApplicationNamesInfo.getInstance().fullProductName)
    if (confirmRestart(message)) {
      restart()
    }
  }

  private fun restart() {
    val app = ApplicationManagerEx.getApplicationEx()
    app.invokeLater {
      app.restart(true)
    }
  }

  private fun confirmRestart(message: @NlsContexts.DialogMessage String): Boolean = Messages.OK == showOkCancelDialog(
    title = ConfigurationStoreBundle.message("import.settings.confirmation.title"),
    message = message,
    okText = getRestartActionName(),
    icon = Messages.getQuestionIcon()
  )

  private fun getRestartActionName(): @NlsContexts.Button String = when {
    ApplicationManager.getApplication().isRestartCapable -> ConfigurationStoreBundle.message("import.settings.confirmation.button.restart")
    else -> ConfigurationStoreBundle.message("import.default.settings.confirmation.button.shutdown")
  }

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
    for (@Suppress("DestructuringDeclaration") item in chosenComponents) {
      result.add(item.fileSpec.relativePath)
    }
    result.add(PluginManager.INSTALLED_TXT)
    return result
  }

  private fun getPaths(saveFile: Path): Set<String> {
    val result = mutableSetOf<String>()
    ZipInputStream(saveFile.inputStream()).use { zipIn ->
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
}
