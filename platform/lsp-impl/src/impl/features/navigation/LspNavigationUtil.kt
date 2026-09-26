package com.intellij.platform.lsp.impl.features.navigation

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.util.getOffsetInDocument
import org.eclipse.lsp4j.Position

internal fun navigateToLspPosition(
  virtualFile: VirtualFile,
  project: Project,
  position: Position,
  requestFocus: Boolean,
) {
  val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return

  getOffsetInDocument(document, position)?.let { offset ->
    FileEditorManager.getInstance(project).openEditor(
      OpenFileDescriptor(project, virtualFile, offset),
      requestFocus
    )
  }
}