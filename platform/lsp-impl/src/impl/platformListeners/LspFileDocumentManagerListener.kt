package com.intellij.platform.lsp.impl.platformListeners

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.lsp.impl.documentSync.LspOpenedFilesService
import com.intellij.platform.lsp.impl.LspClientManagerImpl

internal class LspFileDocumentManagerListener : FileDocumentManagerListener {

  override fun unsavedDocumentDropped(document: Document) = beforeDocumentSaving(document)

  override fun beforeDocumentSaving(document: Document) {
    val file = FileDocumentManager.getInstance().getFile(document) ?: return
    if (!file.isInLocalFileSystem) return

    for (project in ProjectManager.getInstance().openProjects) {
      for (lspClient in LspClientManagerImpl.getInstanceImpl(project).getClientsWithThisFileOpen(file)) {
        lspClient.documentSyncManager.scheduleSave(file, document)
      }

      if (!FileEditorManager.getInstance(project).isFileOpen(file)) {
        LspOpenedFilesService.getInstance(project).scheduleClosingFilesThatAreNotOfInterest()
      }
    }
  }
}
