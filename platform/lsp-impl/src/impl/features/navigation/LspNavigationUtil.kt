package com.intellij.platform.lsp.impl.features.navigation

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.util.getOffsetInDocument
import org.eclipse.lsp4j.Position

/**
 * Path of [fileOrDir] relative to the closest of the server [roots], for showing where a reference or a link leads.
 *
 * Falls back to the full path for anything outside the roots -- an SDK, a dependency cache, a file elsewhere on the disk --
 * where the absolute location is what tells the user where they are going.
 */
internal fun getPresentablePath(fileOrDir: VirtualFile, roots: List<VirtualFile>): @NlsSafe String =
  roots
    .mapNotNull { root -> VfsUtilCore.getRelativePath(fileOrDir, root) }
    .filter { it.isNotEmpty() }
    .minByOrNull { it.length }
  ?: fileOrDir.presentableUrl

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