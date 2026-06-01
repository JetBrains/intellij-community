package com.intellij.platform.lsp.impl.platformListeners

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.lsp.impl.LspOpenedFilesService
import com.intellij.platform.lsp.impl.LspServerManagerImpl

internal class LspFileDocumentManagerListener : FileDocumentManagerListener {

  override fun unsavedDocumentDropped(document: Document) = beforeDocumentSaving(document)

  override fun beforeDocumentSaving(document: Document) {
    val file = FileDocumentManager.getInstance().getFile(document) ?: return
    if (!file.isInLocalFileSystem) return

    for (project in ProjectManager.getInstance().openProjects) {
      for (lspServer in LspServerManagerImpl.getInstanceImpl(project).getServersWithThisFileOpen(file)) {
        lspServer.documentSyncManager.scheduleSave(file, document)
      }

      if (!FileEditorManager.getInstance(project).isFileOpen(file)) {
        LspOpenedFilesService.getInstance(project).scheduleClosingFilesThatAreNotOfInterest()
      }
    }
  }
}
