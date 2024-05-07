// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectsManager
import com.intellij.lang.LangBundle
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.CheckBoxList
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.Action
import javax.swing.JComponent

internal class NewWorkspaceDialog(
  val project: Project,
  initialProjects: List<String>
) : DialogWrapper(project) {
  private lateinit var nameField: JBTextField
  private lateinit var locationField: TextFieldWithBrowseButton
  private val projectList = CheckBoxList<String>()

  val projectName: String get() = nameField.text
  val location: Path get() = Paths.get(locationField.text)
  val projectPath: Path get() = location.resolve(nameField.text)

  init {
    if (project.isWorkspace) {
      title = LangBundle.message("manage.workspace.dialog.title")
    }
    else {
      title = LangBundle.message("new.workspace.dialog.title")
      okAction.putValue(Action.NAME, IdeBundle.message("button.create"))
    }
    initialProjects.forEach {
      @Suppress("HardCodedStringLiteral")
      projectList.addItem(it, it, true)
    }
    init()
  }

  val selectedPaths: List<String>
    get()  = projectList.checkedItems

  override fun createCenterPanel(): JComponent {
    val suggestLocation = RecentProjectsManager.getInstance().suggestNewProjectLocation()
    val suggestName = if (project.isWorkspace)
      project.name
    else
      FileUtil.createSequentFileName(File(suggestLocation),
                                     LangBundle.message("new.workspace.dialog.default.workspace.name"), "") { !it.exists() }

    val toolbarDecorator = ToolbarDecorator.createDecorator(projectList)
      .setPanelBorder(IdeBorderFactory.createTitledBorder(LangBundle.message("border.title.linked.projects")))
      .disableUpDownActions()
      .disableRemoveAction()
      .setAddActionName(LangBundle.message("action.add.projects.text"))
      .setAddAction { addProjects() }
    return panel {
      row(LangBundle.message("new.workspace.dialog.name.label")) {
        nameField = textField()
          .text(suggestName)
          .columns(COLUMNS_MEDIUM)
          .align(Align.FILL)
          .component
      }
      if (!project.isWorkspace) {
        row(LangBundle.message("new.workspace.dialog.location.label")) {
          val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
          descriptor.isHideIgnored = false
          descriptor.title = LangBundle.message("chooser.title.select.workspace.directory")

          locationField = textFieldWithBrowseButton(project = project, fileChooserDescriptor = descriptor)
            .text(suggestLocation)
            .columns(COLUMNS_MEDIUM)
            .align(Align.FILL)
            .component
        }
      }
      row {
        cell(toolbarDecorator.createPanel()).align(Align.FILL)
      }
      if (!project.isWorkspace) {
        row {
          comment(LangBundle.message("new.workspace.dialog.hint"), maxLineLength = 60)
        }
      }
    }
  }

  override fun doOKAction() {
    if (!project.isWorkspace) {
      RecentProjectsManager.getInstance().setLastProjectCreationLocation(location)
    }
    super.doOKAction()
  }

  private fun addProjects() {
    val handlers = SubprojectHandler.EP_NAME.extensionList
    val descriptor = FileChooserDescriptor(true, true, false, false, false, true)
    descriptor.title = LangBundle.message("chooser.title.select.file.or.directory.to.import")
    descriptor.withFileFilter { file -> handlers.any { it.canImportFromFile(project, file) } }
    val files = FileChooser.chooseFiles(descriptor, project, null)
    val allItems = projectList.allItems
    for (file in files) {
      val path = file.path
      if (allItems.contains(path)) continue
      projectList.addItem(path, path, true)
    }
  }
}