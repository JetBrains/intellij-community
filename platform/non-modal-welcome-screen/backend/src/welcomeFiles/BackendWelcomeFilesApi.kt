// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.backend.welcomeFiles

import com.intellij.ide.actions.WelcomeFilesRootType
import com.intellij.ide.actions.deleteWelcomeFile
import com.intellij.ide.actions.saveWelcomeFileAs
import com.intellij.ide.ui.WindowFocusFrontendService
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.nonModalWelcomeScreen.welcomeFiles.WelcomeFilesApi
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

internal class BackendWelcomeFilesApi : WelcomeFilesApi {
  override suspend fun isWelcomeFile(projectId: ProjectId, file: VirtualFileId): Boolean {
    val project = projectId.findProjectOrNull() ?: return false
    return findWelcomeFile(project, file) != null
  }

  override suspend fun saveAs(projectId: ProjectId, file: VirtualFileId): Deferred<Unit> {
    val project = projectId.findProjectOrNull() ?: return CompletableDeferred(Unit)
    return BackendWelcomeFilesService.getInstance(project).saveAs(file)
  }

  override suspend fun discard(projectId: ProjectId, file: VirtualFileId) {
    val project = projectId.findProjectOrNull() ?: return
    withContext(Dispatchers.EDT) {
      val welcomeFile = findWelcomeFile(project, file) ?: return@withContext
      deleteWelcomeFile(project, welcomeFile)
    }
  }
}

/**
 * Runs the save dialogs of the Home files of [project]. A dialog outlives the RPC call that starts it.
 */
@Service(Service.Level.PROJECT)
private class BackendWelcomeFilesService(private val project: Project, private val scope: CoroutineScope) {
  fun saveAs(fileId: VirtualFileId): Deferred<Unit> {
    return scope.async(Dispatchers.EDT) {
      val file = findWelcomeFile(project, fileId) ?: return@async
      // The dialog gets the last focused frontend window as its parent.
      WindowFocusFrontendService.getInstance().performActionWithFocus(true) {
        saveWelcomeFileAs(project, file, closeCurrentTab = true)
      }
    }
  }

  companion object {
    fun getInstance(project: Project): BackendWelcomeFilesService = project.service()
  }
}

/**
 * Returns the file only for a valid Home file of the welcome [project].
 * For an action, call it on the EDT right before the action, because an earlier action can delete the file.
 */
private fun findWelcomeFile(project: Project, fileId: VirtualFileId): VirtualFile? {
  val file = fileId.virtualFile()?.takeIf { it.isValid } ?: return null
  return file.takeIf { WelcomeFilesRootType.Util.isWelcomeFile(project, it) }
}

internal class BackendWelcomeFilesApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<WelcomeFilesApi>()) {
      BackendWelcomeFilesApi()
    }
  }
}
