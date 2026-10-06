// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.frontend.welcomeFiles

import com.intellij.ide.actions.askSaveWelcomeFile
import com.intellij.ide.welcomeScreen.WelcomeUtils
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.tabActions.ALWAYS_SHOW_MODIFIED_MARKER
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePreCloseCheck

/**
 * Asks the user to save each Home file before its tab closes. The check acts only on a file with [ALWAYS_SHOW_MODIFIED_MARKER].
 * The first "Cancel" stops the questions. The files with "Save" and "Don't save" answers before it still get their action.
 * A file stays open without a question while its save dialog is not closed.
 */
internal class WelcomeFilePreCloseCheck : VirtualFilePreCloseCheck {
  override fun canCloseFile(file: VirtualFile): Boolean = canCloseFiles(listOf(file))

  override fun canCloseFiles(files: Collection<VirtualFile>): Boolean = filterFilesToClose(files).size == files.size

  override fun filterFilesToClose(files: Collection<VirtualFile>): Collection<VirtualFile> {
    val filesToSave = ArrayList<Pair<Project, VirtualFile>>()
    val filesToDiscard = ArrayList<Pair<Project, VirtualFile>>()
    val filesToKeep = HashSet<VirtualFile>()
    var cancelled = false

    for (file in files) {
      if (file.getUserData(ALWAYS_SHOW_MODIFIED_MARKER) != true) {
        continue
      }
      val project = findWelcomeProject(file) ?: continue
      if (cancelled || FrontendWelcomeFilesService.getInstance(project).isSaving(file)) {
        filesToKeep += file
        continue
      }
      when (askSaveWelcomeFile(file, project)) {
        Messages.OK -> {
          // The tab stays open. The backend closes it when the user selects the target.
          filesToSave += project to file
          filesToKeep += file
        }
        Messages.NO -> filesToDiscard += project to file
        else -> {
          cancelled = true
          filesToKeep += file
        }
      }
    }

    for ((project, projectFiles) in filesToDiscard.groupBy({ it.first }, { it.second })) {
      FrontendWelcomeFilesService.getInstance(project).discard(projectFiles)
    }
    for ((project, projectFiles) in filesToSave.groupBy({ it.first }, { it.second })) {
      FrontendWelcomeFilesService.getInstance(project).saveAs(projectFiles)
    }
    return if (filesToKeep.isEmpty()) files else files.filter { it !in filesToKeep }
  }

  private fun findWelcomeProject(file: VirtualFile): Project? {
    return getOpenedProjects().firstOrNull { project ->
      WelcomeUtils.isWelcomeProject(project) && FileEditorManager.getInstance(project).isFileOpen(file)
    }
  }
}
