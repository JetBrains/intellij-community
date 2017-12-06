// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.project

import com.intellij.ide.ExporterToTextFile
import com.intellij.ide.actions.ExportToTextFileAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.ui.UIUtil
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.event.ChangeListener

class LoadModuleNameMappingAction(private val dialog: ConvertModuleGroupsToQualifiedNamesDialog) : AbstractAction() {
  init {
    UIUtil.setActionNameAndMnemonic(ProjectBundle.message("module.name.mapping.load.button.text"), this)
  }

  override fun actionPerformed(e: ActionEvent?) {
    val toSelect = getDefaultMappingFilePath(
      dialog.project)?.let { LocalFileSystem.getInstance().refreshAndFindFileByPath(it) }
    val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor(StdFileTypes.PLAIN_TEXT)
    val file = FileChooser.chooseFile(descriptor, dialog.project, toSelect) ?: return

    fun showError(line: Int, message: String) {
      Messages.showErrorDialog(dialog.project, ProjectBundle.message("convert.module.groups.error.text", line + 1, message),
                               ProjectBundle.message("module.name.mapping.cannot.import.error.title"))
    }

    val mappingText = VfsUtil.loadText(file)
    val lines = mappingText.lineSequence()
    val moduleManager = ModuleManager.getInstance(dialog.project)
    lines.forEachIndexed {line, string ->
      if (OLD_NEW_SEPARATOR !in string) {
        showError(line, ProjectBundle.message("module.name.mapping.delimiter.not.present.error", OLD_NEW_SEPARATOR))
        return
      }
      val oldModuleName = string.substringBefore(OLD_NEW_SEPARATOR)
      if (moduleManager.findModuleByName(oldModuleName) == null) {
        showError(line, ProjectBundle.message("module.name.mapping.unknown.module.error", oldModuleName))
        return
      }
    }

    dialog.importMapping(lines.associateBy({ it.substringBefore(OLD_NEW_SEPARATOR) }, { it.substringAfter(OLD_NEW_SEPARATOR) }))
  }
}

class SaveModuleNameMappingAction(private val dialog: ConvertModuleGroupsToQualifiedNamesDialog) : AbstractAction() {
  init {
    UIUtil.setActionNameAndMnemonic(ProjectBundle.message("module.name.mapping.save.button.text"), this)
  }

  override fun actionPerformed(e: ActionEvent?) {
    ExportToTextFileAction.export(dialog.project, ModuleNameMappingExporter(dialog))
  }
}

class ModuleNameMappingExporter(private val dialog: ConvertModuleGroupsToQualifiedNamesDialog) : ExporterToTextFile {
  override fun getReportText() = dialog.getMapping().map { "${it.key}${OLD_NEW_SEPARATOR}${it.value}" }.joinToString("\n") ?: ""

  override fun getDefaultFilePath() = getDefaultMappingFilePath(
    dialog.project) ?: dialog.project.basePath?.let { "$it/modules-rename-mapping.txt" } ?: ""

  override fun exportedTo(filePath: String?) {
    saveDefaultMappingFilePath(dialog.project, filePath)
  }

  override fun canExport() = true

  override fun getSettingsEditor() = null

  override fun addSettingsChangedListener(listener: ChangeListener?) {}

  override fun removeSettingsChangedListener(listener: ChangeListener?) {}
}

private val OLD_NEW_SEPARATOR = "->"
private val EXPORTED_PATH_PROPERTY = "multiple.module.rename.mapping.file"

private fun saveDefaultMappingFilePath(project: Project, filePath: String?) {
  PropertiesComponent.getInstance(project).setValue(EXPORTED_PATH_PROPERTY, filePath)
}

private fun getDefaultMappingFilePath(project: Project) =
  PropertiesComponent.getInstance(project).getValue(EXPORTED_PATH_PROPERTY)
