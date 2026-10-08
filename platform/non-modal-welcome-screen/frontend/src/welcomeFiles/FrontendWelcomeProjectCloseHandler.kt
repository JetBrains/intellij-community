// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.frontend.welcomeFiles

import com.intellij.ide.actions.askSaveWelcomeFiles
import com.intellij.ide.welcomeScreen.WelcomeUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.tabActions.ALWAYS_SHOW_MODIFIED_MARKER
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseHandler
import com.intellij.openapi.ui.Messages

/**
 * Asks the user to save the remote Home files before the welcome project closes on the frontend.
 * A remote Home file has [ALWAYS_SHOW_MODIFIED_MARKER] and lives on the backend of a split session.
 * A local Home file gets its question from `WelcomeProjectCloseHandler`. The application exit asks nothing.
 */
internal class FrontendWelcomeProjectCloseHandler : ProjectCloseHandler {
  override fun canClose(project: Project): Boolean {
    if (ApplicationManager.getApplication().isExitInProgress || !WelcomeUtils.isWelcomeProject(project)) {
      return true
    }
    val files = FileEditorManager.getInstance(project).openFiles.filter {
      it.getUserData(ALWAYS_SHOW_MODIFIED_MARKER) == true && !it.isInLocalFileSystem
    }
    if (files.isEmpty()) {
      return true
    }
    val service = FrontendWelcomeFilesService.getInstance(project)
    return when (askSaveWelcomeFiles(files, project)) {
      Messages.CANCEL -> false
      Messages.OK -> service.saveOnClose(files)
      else -> {
        service.discardOnClose(files)
        true
      }
    }
  }
}
