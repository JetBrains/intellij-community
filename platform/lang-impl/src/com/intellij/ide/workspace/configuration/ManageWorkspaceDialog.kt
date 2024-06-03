// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace.configuration

import com.intellij.ide.workspace.SubprojectHandler
import com.intellij.ide.workspace.addToWorkspace
import com.intellij.ide.workspace.projectView.isWorkspaceNode
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent

internal class ManageWorkspaceDialog(private val project: Project) : DialogWrapper(project) {
  private lateinit var nameField: JBTextField
  private lateinit var locationField: TextFieldWithBrowseButton
  private val subprojectList: SubprojectList = SubprojectList(project)

  val projectName: String get() = nameField.text
  val location: Path get() = Paths.get(locationField.text)
  val projectPath: Path get() = location.resolve(nameField.text)

  init {
    title = LangBundle.message("manage.workspace.dialog.title")
    init()
  }

  val projectPaths: List<String>
    get()  = subprojectList.projectPaths

  override fun createCenterPanel(): JComponent {

    return panel {
      row(LangBundle.message("new.workspace.dialog.name.label")) {
        nameField = textField()
          .text(project.name)
          .columns(COLUMNS_MEDIUM)
          .align(Align.FILL)
          .component
      }
      row {
        cell(subprojectList.createDecorator().createPanel()).align(Align.FILL)
      }
    }
  }
}

internal fun browseForProjects(project: Project?): Array<out VirtualFile> {
  val handlers = SubprojectHandler.EP_NAME.extensionList
  val descriptor = FileChooserDescriptor(true, true, false, false, false, true)
  descriptor.title = LangBundle.message("chooser.title.select.file.or.directory.to.import")
  descriptor.withFileFilter { file -> handlers.any { it.canImportFromFile(file) } }
  return FileChooser.chooseFiles(descriptor, project, project?.guessProjectDir()?.parent)
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