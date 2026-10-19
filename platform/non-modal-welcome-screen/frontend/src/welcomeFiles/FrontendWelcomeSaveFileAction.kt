// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.frontend.welcomeFiles

import com.intellij.ide.welcomeScreen.WelcomeUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.tabActions.ALWAYS_SHOW_MODIFIED_MARKER
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Saves the Home file of an editor tab to a target the user selects. The editor tab menu shows the action.
 * The action finds a Home file by [ALWAYS_SHOW_MODIFIED_MARKER], so it also finds a remote Home file on a split-mode frontend.
 * [FrontendWelcomeFilesService] runs the save dialog on the backend.
 */
internal class FrontendWelcomeSaveFileAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val file = getWelcomeFile(project, e)
    e.presentation.isEnabledAndVisible = file != null && !FrontendWelcomeFilesService.getInstance(project).isSaving(file)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val file = getWelcomeFile(project, e) ?: return
    FrontendWelcomeFilesService.getInstance(project).saveAs(listOf(file))
  }

  /**
   * Returns the Home file of the tab under the menu. Without a tab, it returns the Home file of the editor in the context.
   */
  private fun getWelcomeFile(project: Project, e: AnActionEvent): VirtualFile? {
    if (!WelcomeUtils.isWelcomeProject(project)) {
      return null
    }
    val file = e.getData(EditorWindow.DATA_KEY)?.getContextFile()
               ?: e.getData(CommonDataKeys.EDITOR)?.document?.let { FileDocumentManager.getInstance().getFile(it) }
               ?: return null
    return file.takeIf { it.getUserData(ALWAYS_SHOW_MODIFIED_MARKER) == true }
  }
}
