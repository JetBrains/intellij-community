package com.intellij.platform.lsp.impl.platformListeners

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.impl.documentSync.LspOpenedFilesService
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.features.inlayCommon.LspInlayApplier

internal class LspFileEditorManagerListener : FileEditorManagerListener {
  override fun fileOpened(fileEditorManager: FileEditorManager, file: VirtualFile) {
    val project = fileEditorManager.project.takeIf { !it.isDefault } ?: return
    if (!file.isInLocalFileSystem) return
    LspOpenedFilesService.getInstance(project).processOpenedFiles(listOf(file))
  }

  override fun selectionChanged(event: FileEditorManagerEvent) {
    val project = event.manager.project.takeIf { !it.isDefault } ?: return
    val file = event.newFile?.takeIf { it.isInLocalFileSystem } ?: return
    LspOpenedFilesService.getInstance(project).processOpenedFiles(listOf(file))
  }

  override fun fileClosed(fileEditorManager: FileEditorManager, file: VirtualFile) {
    val project = fileEditorManager.project.takeIf { !it.isDefault } ?: return
    if (!file.isInLocalFileSystem) return
    if (fileEditorManager.isFileOpen(file)) return // the file might be still open in some other editor
    LspInlayApplier.getInstance(project).onFileClosed(file)
    val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return
    if (FileDocumentManager.getInstance().isDocumentUnsaved(document)) return
    val serversToSendDidClose = LspClientManagerImpl.getInstanceImpl(project).getClientsWithThisFileOpen(file)
    if (serversToSendDidClose.isNotEmpty()) {
      WriteAction.run<RuntimeException> { serversToSendDidClose.forEach { it.documentSyncManager.close(file) } }
    }
  }
}
