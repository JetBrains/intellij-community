// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.project

import com.intellij.CommonBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.ModuleRenamingHistoryState
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.serialization.SerializationException
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.XmlSerializer
import java.awt.event.ActionEvent
import javax.swing.AbstractAction

internal class LoadModuleRenamingSchemeAction(private val dialog: ConvertModuleGroupsToQualifiedNamesDialog) : AbstractAction() {
  init {
    UIUtil.setActionNameAndMnemonic(ProjectBundle.message("module.renaming.scheme.load.button.text"), this)
  }

  override fun actionPerformed(e: ActionEvent?) {
    val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor(StdFileTypes.XML)
    val file = FileChooser.chooseFile(descriptor, dialog.project, getDefaultRenamingSchemeFile(dialog.project)) ?: return

    fun showError(message: String) {
      Messages.showErrorDialog(dialog.project, ProjectBundle.message("module.renaming.scheme.cannot.load.error", file.presentableUrl, message),
                               ProjectBundle.message("module.renaming.scheme.cannot.import.error.title"))
    }

    val renamingState = try {
      XmlSerializer.deserialize(JDOMUtil.load(file.inputStream), ModuleRenamingHistoryState::class.java)
    }
    catch (e: SerializationException) {
      LOG.info(e)
      showError(e.message ?: "unknown error")
      return
    }
    val moduleManager = ModuleManager.getInstance(dialog.project)
    renamingState.oldToNewName.keys.forEach {
      if (moduleManager.findModuleByName(it) == null) {
        showError(ProjectBundle.message("module.renaming.scheme.unknown.module.error", it))
        return
      }
    }

    dialog.importRenamingScheme(renamingState.oldToNewName)
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
      IdeFocusManager.getGlobalInstance().requestFocus(dialog.preferredFocusedComponent, true)
    }
  }
  companion object {
    val LOG = Logger.getInstance(LoadModuleRenamingSchemeAction::class.java)
  }
}

internal class SaveModuleRenamingSchemeAction(private val dialog: ConvertModuleGroupsToQualifiedNamesDialog, private val onSaved: () -> Unit) : AbstractAction() {
  init {
    UIUtil.setActionNameAndMnemonic(ProjectBundle.message("module.renaming.scheme.save.button.text"), this)
  }

  override fun actionPerformed(e: ActionEvent?) {
    if (saveModuleRenamingScheme(dialog)) {
      onSaved()
    }
  }
}

internal fun saveModuleRenamingScheme(dialog: ConvertModuleGroupsToQualifiedNamesDialog): Boolean {
  val project = dialog.project
  val descriptor = FileSaverDescriptor(ProjectBundle.message("module.renaming.scheme.save.chooser.title"),
                                       ProjectBundle.message("module.renaming.scheme.save.chooser.description"), "xml")
  val baseDir = getDefaultRenamingSchemeFile(project)?.parent ?: project.baseDir
  val fileWrapper = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project).save(baseDir, "module-renaming-scheme.xml")
  if (fileWrapper != null) {
    saveDefaultRenamingSchemeFilePath(project, FileUtil.toSystemIndependentName(fileWrapper.file.absolutePath))

    val state = ModuleRenamingHistoryState()
    state.oldToNewName.putAll(dialog.getRenamingScheme())
    try {
      JDOMUtil.write(XmlSerializer.serialize(state), fileWrapper.file.toPath())
      fileWrapper.virtualFile?.refresh(true, false)
      return true
    }
    catch (e: Exception) {
      LoadModuleRenamingSchemeAction.LOG.info(e)
      Messages.showErrorDialog(project, CommonBundle.getErrorTitle(),
                               ProjectBundle.message("module.renaming.scheme.cannot.save.error", e.message ?: ""))
    }
  }
  return false
}

private const val EXPORTED_PATH_PROPERTY = "module.renaming.scheme.file"

private fun saveDefaultRenamingSchemeFilePath(project: Project, filePath: String?) {
  PropertiesComponent.getInstance(project).setValue(EXPORTED_PATH_PROPERTY, filePath)
}

private fun getDefaultRenamingSchemeFile(project: Project) =
  PropertiesComponent.getInstance(project).getValue(EXPORTED_PATH_PROPERTY)?.let { LocalFileSystem.getInstance().refreshAndFindFileByPath(it) }
