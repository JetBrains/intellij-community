// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.workspace.projectView.isWorkspaceNode
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CollectionListModel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
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
  private val listModel = CollectionListModel(initialProjects)
  private val projectList = JBList(listModel)

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
    init()
  }

  val selectedPaths: List<String>
    get()  = listModel.items

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
    val files = browseForProjects(project)
    val allItems = listModel.items
    for (file in files) {
      val path = file.path
      if (allItems.contains(path)) continue
      listModel.add(path)
    }
  }
}

private fun browseForProjects(project: Project): Array<out VirtualFile> {
  val handlers = SubprojectHandler.EP_NAME.extensionList
  val descriptor = FileChooserDescriptor(true, true, false, false, false, true)
  descriptor.title = LangBundle.message("chooser.title.select.file.or.directory.to.import")
  descriptor.withFileFilter { file -> handlers.any { it.canImportFromFile(project, file) } }
  return FileChooser.chooseFiles(descriptor, project, project.guessProjectDir()?.parent)
}

internal class AddProjectsToWorkspaceAction: BaseWorkspaceAction(true) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = requireNotNull(e.project)
    val files = browseForProjects(project)
    if (files.isEmpty()) return
    addToWorkspace(project, files.map { it.path })
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isWorkspaceNode(e)
  }
}