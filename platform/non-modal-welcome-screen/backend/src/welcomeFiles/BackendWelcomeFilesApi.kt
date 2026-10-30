// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.backend.welcomeFiles

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.WelcomeFilesRootType
import com.intellij.ide.actions.deleteWelcomeFile
import com.intellij.ide.actions.deleteWelcomeFilesOnClose
import com.intellij.ide.actions.saveWelcomeFileAs
import com.intellij.ide.actions.saveWelcomeFilesOnClose
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchLanguageUtil
import com.intellij.ide.ui.WindowFocusFrontendService
import com.intellij.ide.ui.icons.rpcIdOrNull
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.platform.ide.nonModalWelcomeScreen.welcomeFiles.WelcomeFileLanguage
import com.intellij.platform.ide.nonModalWelcomeScreen.welcomeFiles.WelcomeFilesApi
import com.intellij.platform.ide.nonModalWelcomeScreen.welcomeFiles.WelcomeLanguageItem
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
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

  override suspend fun saveOnClose(projectId: ProjectId, files: List<VirtualFileId>): Deferred<Boolean> {
    val project = projectId.findProjectOrNull() ?: return CompletableDeferred(true)
    return BackendWelcomeFilesService.getInstance(project).saveOnClose(files)
  }

  override suspend fun discardOnClose(projectId: ProjectId, files: List<VirtualFileId>) {
    val project = projectId.findProjectOrNull() ?: return
    withContext(Dispatchers.EDT) {
      val welcomeFiles = files.mapNotNull { findWelcomeFile(project, it) }
      if (welcomeFiles.isNotEmpty()) {
        deleteWelcomeFilesOnClose(project, welcomeFiles)
      }
    }
  }

  override suspend fun fileLanguage(projectId: ProjectId, file: VirtualFileId): Flow<WelcomeFileLanguage> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    val welcomeFile = findWelcomeFile(project, file) ?: return emptyFlow()
    return channelFlow {
      val changes = Channel<Unit>(Channel.CONFLATED)
      changes.send(Unit)
      // A language change also sends a name change event, because it reparses the file.
      ApplicationManager.getApplication().messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
        override fun after(events: List<VFileEvent>) {
          if (events.any { it is VFilePropertyChangeEvent && it.propertyName == VirtualFile.PROP_NAME && it.file == welcomeFile }) {
            changes.trySend(Unit)
          }
        }
      })
      for (change in changes) {
        val language = readAction { if (welcomeFile.isValid) getFileLanguage(project, welcomeFile) else null } ?: break
        send(language)
      }
    }
  }

  override suspend fun getLanguages(): List<WelcomeLanguageItem> {
    return ScratchLanguageUtil.getLanguageItems().map {
      WelcomeLanguageItem(it.id, it.displayName, it.fileExtension(), it.icon.rpcIdOrNull())
    }
  }

  override suspend fun changeLanguage(projectId: ProjectId, file: VirtualFileId, languageId: String) {
    val project = projectId.findProjectOrNull() ?: return
    withContext(Dispatchers.EDT) {
      val welcomeFile = findWelcomeFile(project, file) ?: return@withContext
      val item = ScratchLanguageUtil.getLanguageItems().find { it.id == languageId } ?: return@withContext
      ScratchLanguageUtil.changeLanguage(project, item, arrayOf(welcomeFile), ScratchFileService.getInstance().scratchesMapping)
    }
  }
}

/**
 * Returns the language of the Home [file], as the "Change Language" action of the editor menu shows it.
 */
private fun getFileLanguage(project: Project, file: VirtualFile): WelcomeFileLanguage {
  // A file type without a language gets no language substitution.
  val language = if (LanguageUtil.getFileLanguage(file) == null) null
  else ScratchFileService.getInstance().scratchesMapping.getMapping(file) ?: LanguageUtil.getLanguageForPsi(project, file)
  val fileType = language?.associatedFileType ?: FileTypeManager.getInstance().getFileTypeByFileName(file.name)
  val icon = fileType.icon ?: AllIcons.FileTypes.Any_type
  return WelcomeFileLanguage(language?.displayName ?: fileType.displayName, icon.rpcIdOrNull())
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

  fun saveOnClose(fileIds: List<VirtualFileId>): Deferred<Boolean> {
    return scope.async(Dispatchers.EDT) {
      val files = fileIds.mapNotNull { findWelcomeFile(project, it) }
      if (files.isEmpty()) {
        return@async true
      }
      // The dialog gets the last focused frontend window as its parent.
      WindowFocusFrontendService.getInstance().performActionWithFocus(true) {
        saveWelcomeFilesOnClose(project, files)
      } == true
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
