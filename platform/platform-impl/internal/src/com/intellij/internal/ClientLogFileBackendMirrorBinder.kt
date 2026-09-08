// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.idea.LoggerFactory
import com.intellij.ide.ui.icons.rpcIdOrNull
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FrontendOwnedBackendMirrorFileDescriptor
import com.intellij.openapi.fileEditor.impl.FrontendOwnedFileEditorSplitBinder
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ClientLogFileBackendMirrorBinder {

  @JvmStatic
  fun bindToBackendMirror(event: AnActionEvent, project: Project, file: VirtualFile) {
    event.coroutineScope.launch {
      bindToBackendMirror(project, file)
    }
  }

  suspend fun bindOpenLogToBackendMirror(project: Project) {
    val logFile = LocalFileSystem.getInstance().findFileByNioFile(LoggerFactory.getLogFilePath()) ?: return
    bindToBackendMirror(project, logFile)
  }

  suspend fun bindToBackendMirror(project: Project, file: VirtualFile) {
    val editorWithProvider = withContext(Dispatchers.EDT) {
      FileEditorManagerEx.getInstanceEx(project).getSelectedEditorWithProvider(file)
    } ?: return

    FrontendOwnedFileEditorSplitBinder.tryBindExistingFrontendEditorToBackendMirror(
      project = project,
      file = file,
      provider = editorWithProvider.provider,
      editor = editorWithProvider.fileEditor,
      mirrorFile = FrontendOwnedBackendMirrorFileDescriptor(
        namespace = FRONTEND_LOG_BACKEND_MIRROR_NAMESPACE,
        id = file.url,
        presentableName = file.presentableName,
        iconId = file.fileType.icon?.rpcIdOrNull(),
      ),
    )
  }
}

private const val FRONTEND_LOG_BACKEND_MIRROR_NAMESPACE: String = "platform.frontend.log"
