// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorOpenMode
import com.intellij.openapi.fileEditor.FileEditorOpenRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

interface DiffEditorTabFilesManager {

  /**
   * Opens [diffFile] in a blocking manner.
   * Prefer [requestShowDiffFile], or [openDiffFile] in a coroutine.
   */
  fun showDiffFile(diffFile: VirtualFile, focusEditor: Boolean): Array<out FileEditor> {
    DiffEditorTabFilesUtil.setForceOpeningsInNewWindow(diffFile, null)
    return showDiffFile(diffFile, focusEditor, DiffEditorTabFilesUtil.isDiffInEditor)
  }

  fun showDiffFile(diffFile: VirtualFile, focusEditor: Boolean, openInEditor: Boolean): Array<out FileEditor>

  /**
   * Submits an open request of [diffFile] and does not wait for its editors. The tab and the focus come later.
   * The request always reuses the open tab of [diffFile].
   * The future completes with the editors. A cancel of the future before the tab opens stops the request.
   * An explicit [FileEditorOpenMode.NEW_WINDOW] pins [diffFile] to its window: the move actions and the reopen
   * on a changed diff-in-editor setting skip the file, unlike the blocking `showDiffFile(file, focus, false)`.
   *
   * @see [FileEditorOpenMode] for details
   */
  @ApiStatus.Experimental
  fun requestShowDiffFile(diffFile: VirtualFile, request: FileEditorOpenRequest): CompletableFuture<List<FileEditor>> {
    return CompletableFuture.completedFuture(showDiffFileBlocking(diffFile, request))
  }

  /**
   * Opens [diffFile] and suspends until its editors are ready.
   * Call it from a coroutine on any thread.
   * An explicit [FileEditorOpenMode.NEW_WINDOW] pins [diffFile] to its window, as in [requestShowDiffFile].
   */
  @ApiStatus.Experimental
  suspend fun openDiffFile(diffFile: VirtualFile, request: FileEditorOpenRequest): List<FileEditor> {
    return withContext(Dispatchers.EDT) { showDiffFileBlocking(diffFile, request) }
  }

  private fun showDiffFileBlocking(diffFile: VirtualFile, request: FileEditorOpenRequest): List<FileEditor> {
    val editors = when (request.openMode) {
      FileEditorOpenMode.MANAGED -> showDiffFile(diffFile, request.requestFocus)
      FileEditorOpenMode.NEW_WINDOW -> {
        // the window of an explicit request survives the diff-in-editor setting
        DiffEditorTabFilesUtil.setForceOpeningsInNewWindow(diffFile, true)
        showDiffFile(diffFile, request.requestFocus, false)
      }
      FileEditorOpenMode.DEFAULT, FileEditorOpenMode.RIGHT_SPLIT -> showDiffFile(diffFile, request.requestFocus, true)
    }
    return editors.asList()
  }

  /**
   * Returns true for diff files that are opened in their own window using
   * [com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl.SINGLETON_EDITOR_IN_WINDOW] options
   */
  fun isDiffOpenedInWindow(file: VirtualFile): Boolean

  companion object {

    @JvmStatic
    fun getInstance(project: Project): DiffEditorTabFilesManager = project.service()
  }
}
