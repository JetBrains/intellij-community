// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.backend.welcomeFiles

import com.intellij.ide.actions.WelcomeFilesRootType
import com.intellij.ide.actions.deleteWelcomeFile
import com.intellij.ide.actions.saveWelcomeFileAs
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.nonModalWelcomeScreen.welcomeFiles.WelcomeFilesApi
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class BackendWelcomeFilesApi : WelcomeFilesApi {
  override suspend fun isWelcomeFile(projectId: ProjectId, file: VirtualFileId): Boolean {
    return findWelcomeFile(projectId, file) != null
  }

  override suspend fun saveAs(projectId: ProjectId, file: VirtualFileId) {
    val (project, welcomeFile) = findWelcomeFile(projectId, file) ?: return
    withContext(Dispatchers.EDT) {
      saveWelcomeFileAs(project, welcomeFile, closeCurrentTab = true)
    }
  }

  override suspend fun discard(projectId: ProjectId, file: VirtualFileId) {
    val (project, welcomeFile) = findWelcomeFile(projectId, file) ?: return
    withContext(Dispatchers.EDT) {
      deleteWelcomeFile(project, welcomeFile)
    }
  }

  /**
   * Returns the project and the file only for a Home file of the welcome project.
   */
  private fun findWelcomeFile(projectId: ProjectId, fileId: VirtualFileId): Pair<Project, VirtualFile>? {
    val project = projectId.findProjectOrNull() ?: return null
    val file = fileId.virtualFile() ?: return null
    return if (WelcomeFilesRootType.Util.isWelcomeFile(project, file)) project to file else null
  }
}

internal class BackendWelcomeFilesApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<WelcomeFilesApi>()) {
      BackendWelcomeFilesApi()
    }
  }
}
