// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.editor.DiffEditorTabFilesUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

interface DiffPreview {
  /**
   * Can be called from any context, an implementation switches to the EDT for its UI work.
   * False means that the preview is not shown.
   */
  suspend fun openPreview(requestFocus: Boolean): Boolean
  fun closePreview()

  /**
   * Allows overriding 'Show Diff' action availability and presentation
   */
  fun updateDiffAction(event: AnActionEvent) = Unit

  /**
   * Allows overriding 'Show Diff' action behavior.
   * For example, by using External Diff Tools when applicable.
   * The call does not wait for the preview to show.
   */
  fun performDiffAction(): Boolean

  companion object {
    /**
     * Close all file editors, including not from active splitters, associated with particular [previewFile]
     */
    @JvmStatic
    fun closePreviewFile(project: Project, previewFile: VirtualFile) {
      DiffEditorTabFilesUtil.setForceOpeningsInNewWindow(previewFile, null)
      val editorManager: FileEditorManager = FileEditorManager.getInstance(project)
      when (editorManager) {
        is FileEditorManagerImpl -> editorManager.closeFile(previewFile, closeAllCopies = true, moveFocus = true)
        else -> editorManager.closeFile(previewFile)
      }
    }
  }
}