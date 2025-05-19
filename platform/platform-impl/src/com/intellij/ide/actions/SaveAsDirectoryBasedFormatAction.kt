// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.OpenProjectTask.Companion.build
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

@ApiStatus.Internal
class SaveAsDirectoryBasedFormatAction : AnAction(), DumbAware {
  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    if (!isConvertableProject(project)) {
      return
    }

    if (!MessageDialogBuilder.okCancel(IdeBundle.message("dialog.title.save.project.to.directory.based.format"),
                                       IdeBundle.message("message.project.will.be.saved.and.reopened.in.new.directory.based.format"))
        .icon(Messages.getWarningIcon())
        .ask(project)) {
      return
    }

    val store = project.stateStore
    val baseDir = store.getProjectFilePath().parent
    val ideaDir = baseDir.resolve(Project.DIRECTORY_STORE_FOLDER)
    try {
      convertToDirectoryBasedFormat(project = project, store = store, baseDir = baseDir, ideaDir = ideaDir)
      // closeAndDispose will also force "save project"
      val projectManager = ProjectManagerEx.getInstanceEx()
      projectManager.closeAndDispose(project)
      projectManager.openProject(ideaDir.parent, build())
    }
    catch (e: IOException) {
      Messages.showErrorDialog(project,
                               String.format(IdeBundle.message("dialog.message.unable.to.create.idea.directory", e.message), ideaDir),
                               IdeBundle.message("dialog.title.error.saving.project"))
    }
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.setVisible(isConvertableProject(project))
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private fun isConvertableProject(project: Project?): Boolean {
  return project != null && !project.isDefault && !project.isDirectoryBased
}

private fun convertToDirectoryBasedFormat(project: Project, store: IProjectStore, baseDir: Path, ideaDir: Path) {
  if (Files.isDirectory(ideaDir)) {
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(ideaDir)
  }
  else {
    ApplicationManager.getApplication().runWriteAction { VfsUtil.createDirectoryIfMissing(ideaDir.toString()) }
  }

  store.clearStorages()
  store.setPath(baseDir)
  ApplicationManager.getApplication().runWriteAction { JpsProjectModelSynchronizer.getInstance(project).convertToDirectoryBasedFormat() }
}

@TestOnly
fun convertToDirectoryBasedFormat(project: Project) {
  val store = project.stateStore
  val baseDir = store.getProjectFilePath().parent
  val ideaDir = baseDir.resolve(Project.DIRECTORY_STORE_FOLDER)
  convertToDirectoryBasedFormat(project = project, store = store, baseDir = baseDir, ideaDir = ideaDir)
}