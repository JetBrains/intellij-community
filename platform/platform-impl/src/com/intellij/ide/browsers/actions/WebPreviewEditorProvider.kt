// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers.actions

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class WebPreviewEditorProvider : AsyncFileEditorProvider {
  override fun accept(project: Project, file: VirtualFile): Boolean = file is WebPreviewVirtualFile

  override fun acceptRequiresReadAction(): Boolean = false

  override suspend fun createFileEditor(
    project: Project,
    file: VirtualFile,
    document: Document?,
    editorCoroutineScope: CoroutineScope,
  ): FileEditor {
    val fileDocumentManager = serviceAsync<FileDocumentManager>()
    val editor = withContext(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
      writeIntentReadAction {
        fileDocumentManager.saveAllDocuments()
      }
      WebPreviewFileEditor(file as WebPreviewVirtualFile)
    }
    editor.reloadPage()
    return editor
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val editor = WebPreviewFileEditor(file as WebPreviewVirtualFile)
    FileDocumentManager.getInstance().saveAllDocuments()
    editor.reloadPage()
    return editor
  }

  override fun getEditorTypeId(): String = "web-preview-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

