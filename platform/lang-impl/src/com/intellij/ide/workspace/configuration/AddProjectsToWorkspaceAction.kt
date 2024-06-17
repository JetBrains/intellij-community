// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace.configuration

import com.intellij.ide.workspace.*
import com.intellij.ide.workspace.projectView.isWorkspaceNode
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile

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

internal fun browseForProjects(project: Project?): Array<out VirtualFile> {
  val handlers = SubprojectHandler.EP_NAME.extensionList
  val descriptor = FileChooserDescriptor(true, true, false, false, false, true)
  descriptor.title = LangBundle.message("chooser.title.select.file.or.directory.to.import")
  descriptor.withFileFilter { file -> handlers.any { it.canImportFromFile(file) } }
  return FileChooser.chooseFiles(descriptor, project, project?.guessProjectDir()?.parent)
}

internal abstract class BaseWorkspaceAction(private val workspaceOnly: Boolean): DumbAwareAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT
  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = isWorkspaceSupportEnabled &&
                                         project != null &&
                                         (workspaceOnly && project.isWorkspace
                                          || !workspaceOnly && getAllSubprojects(project).isNotEmpty())
  }
}