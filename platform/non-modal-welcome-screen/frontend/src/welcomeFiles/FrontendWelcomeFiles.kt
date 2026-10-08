// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.frontend.welcomeFiles

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.impl.tabActions.ALWAYS_SHOW_MODIFIED_MARKER
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.isWelcomeExperienceProject
import com.intellij.platform.ide.nonModalWelcomeScreen.welcomeFiles.WelcomeFilesApi
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.project.projectId
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<FrontendWelcomeFilesService>()

/**
 * Sends the requests about the Home files of [project] to [WelcomeFilesApi].
 */
@Service(Service.Level.PROJECT)
internal class FrontendWelcomeFilesService(private val project: Project, private val scope: CoroutineScope) {
  private val savingFiles: MutableSet<VirtualFile> = ConcurrentHashMap.newKeySet()

  /**
   * Puts [ALWAYS_SHOW_MODIFIED_MARKER] on [file] when [file] is a Home file of the welcome project.
   */
  fun markWelcomeFile(file: VirtualFile) {
    if (file.getUserData(ALWAYS_SHOW_MODIFIED_MARKER) == true) {
      return
    }
    scope.launch {
      if (project.isWelcomeExperienceProject() && WelcomeFilesApi.getInstance().isWelcomeFile(project.projectId(), file.rpcId())) {
        file.putUserData(ALWAYS_SHOW_MODIFIED_MARKER, true)
      }
    }
  }

  /**
   * Returns true when the user selected "Save" for [file], and the save dialog of [file] is not closed yet.
   */
  fun isSaving(file: VirtualFile): Boolean = file in savingFiles

  /**
   * Starts "save as" for each Home file in [files]. The save dialogs show one after another, in the order of [files].
   * The backend closes the tab of a file when the user selects its target.
   */
  fun saveAs(files: List<VirtualFile>) {
    savingFiles += files
    val projectId = project.projectId()
    val fileIds = files.associateWith { it.rpcId() }
    scope.launch {
      try {
        for ((file, fileId) in fileIds) {
          try {
            WelcomeFilesApi.getInstance().saveAs(projectId, fileId).await()
          }
          catch (e: Throwable) {
            rethrowControlFlowException(e)
            LOG.error("Cannot save the Home file ${file.name}", e)
          }
          savingFiles -= file
        }
      }
      finally {
        // A cancellation stops the queue, so the files after it leave the saving state here.
        savingFiles -= fileIds.keys
      }
    }
  }

  /**
   * Copies the Home [files] to a target the user selects on the backend, then deletes them.
   * The call blocks until the backend is done, because the project close continues right after it.
   * Returns false when the user cancels the dialog, or when the save fails. Then the project stays open.
   */
  @RequiresEdt
  fun saveOnClose(files: List<VirtualFile>): Boolean {
    val projectId = project.projectId()
    val fileIds = files.map { it.rpcId() }
    val title = NonModalWelcomeScreenBundle.message("welcome.files.save.on.close.progress.title")
    return runWithModalProgressBlocking(ModalTaskOwner.project(project), title, TaskCancellation.nonCancellable()) {
      try {
        WelcomeFilesApi.getInstance().saveOnClose(projectId, fileIds).await()
      }
      catch (e: Throwable) {
        rethrowControlFlowException(e)
        LOG.error("Cannot save the Home files before the project close", e)
        false
      }
    }
  }

  /**
   * Deletes the Home [files] and closes their editors. The call blocks until the backend is done.
   */
  @RequiresEdt
  fun discardOnClose(files: List<VirtualFile>) {
    val projectId = project.projectId()
    val fileIds = files.map { it.rpcId() }
    val title = NonModalWelcomeScreenBundle.message("welcome.files.save.on.close.progress.title")
    runWithModalProgressBlocking(ModalTaskOwner.project(project), title, TaskCancellation.nonCancellable()) {
      try {
        WelcomeFilesApi.getInstance().discardOnClose(projectId, fileIds)
      }
      catch (e: Throwable) {
        rethrowControlFlowException(e)
        LOG.error("Cannot delete the Home files before the project close", e)
      }
    }
  }

  /**
   * Deletes each Home file in [files] after its tab closes. A file with an open tab stays.
   */
  fun discard(files: List<VirtualFile>) {
    val projectId = project.projectId()
    val fileIds = files.associateWith { it.rpcId() }
    scope.launch {
      val closedFileIds = withContext(Dispatchers.EDT) {
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileIds.filterKeys { !fileEditorManager.isFileOpen(it) }
      }
      for ((file, fileId) in closedFileIds) {
        try {
          WelcomeFilesApi.getInstance().discard(projectId, fileId)
        }
        catch (e: Throwable) {
          rethrowControlFlowException(e)
          LOG.error("Cannot delete the Home file ${file.name}", e)
        }
      }
    }
  }

  companion object {
    fun getInstance(project: Project): FrontendWelcomeFilesService = project.service()
  }
}

internal class WelcomeFilesEditorListener(private val project: Project) : FileEditorManagerListener {
  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    FrontendWelcomeFilesService.getInstance(project).markWelcomeFile(file)
  }
}
