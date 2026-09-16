// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.ClientFileEditorManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileOpenedSyncListener
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.headTail

internal class DiffEditorTabFilesManagerImpl(val project: Project) : DiffEditorTabFilesManager {
  override fun showDiffFile(diffFile: VirtualFile, focusEditor: Boolean, openInEditor: Boolean): Array<out FileEditor> {
    val editorManager = FileEditorManager.getInstance(project) as FileEditorManagerImpl
    val openMode = if (openInEditor) FileEditorManagerImpl.OpenMode.DEFAULT else FileEditorManagerImpl.OpenMode.NEW_WINDOW
    val newTab = editorManager.openFile(
      file = diffFile,
      window = null,
      options = FileEditorOpenOptions(
        openMode = openMode,
        isSingletonEditorInWindow = true,
        reuseOpen = true,
        requestFocus = focusEditor,
      ),
    )
    return newTab.allEditors.toTypedArray()
  }

  override fun isDiffOpenedInWindow(file: VirtualFile): Boolean {
    if (file !is DiffContentVirtualFile) return false

    val editorManager = FileEditorManager.getInstance(project)
    if (editorManager !is FileEditorManagerImpl) return false

    val window = editorManager.windows.find { it.isFileOpen(file) } ?: return false
    return isSingletonEditorInWindow(window)
  }
}

@Suppress("RemoveRedundantQualifierName")
internal abstract class MoveDiffEditorAction(private val openInNewWindow: Boolean) : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val editorWindow = e.getData(EditorWindow.DATA_KEY)
    val fileEditor = editorWindow?.selectedComposite?.selectedEditor
    if (fileEditor == null || !IS_DIFF_FILE_EDITOR.isIn(fileEditor) || DiffEditorTabFilesUtil.isForceOpeningsInNewWindow(fileEditor.file)) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val isInWindow = isSingletonEditorInWindow(editorWindow)
    e.presentation.isEnabledAndVisible = isInWindow != openInNewWindow
  }

  override fun actionPerformed(e: AnActionEvent) {
    DiffEditorTabFilesUtil.isDiffInEditor = !openInNewWindow
  }

  internal class ToEditor : MoveDiffEditorAction(false)
  internal class ToWindow : MoveDiffEditorAction(true)
}

internal class EditorTabDiffPreviewAdvancedSettingsListener : AdvancedSettingsChangeListener {
  override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
    if (id == DiffEditorTabFilesUtil.SHOW_DIFF_IN_EDITOR_SETTING) {
      for (project in ProjectManager.getInstance().openProjects) {
        reopenDiffEditorsForFiles(project)
      }
    }
  }

  companion object {
    /**
     * Unlike [com.intellij.diff.editor.DiffEditorViewerFileEditor.reloadDiffEditorsForFiles], should not try to reopen tabs in-place.
     *
     * The setting changes on the host, but a diff can be open in a client (split mode). The host has no
     * floating window for a client diff, so it can not test the current placement here. Every open diff is
     * reopened in the client that owns it. The reopen reads the host setting as the source of truth.
     */
    private fun reopenDiffEditorsForFiles(project: Project) {
      val editorManager = FileEditorManager.getInstance(project) as? FileEditorManagerImpl ?: return
      val diffEditorManager = DiffEditorTabFilesManager.getInstance(project)

      val filesByClient = editorManager.allEditors
        .mapNotNull { editor -> editor.file?.let { ClientFileEditorManager.getClientId(editor) to it } }
        .filter { (_, file) -> file is DiffContentVirtualFile && !DiffEditorTabFilesUtil.isForceOpeningsInNewWindow(file) }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, files) -> files.distinct() }

      // Close every copy first (this path handles all clients and splitters), so the reopen can not reuse a stale one.
      for (files in filesByClient.values) {
        for (file in files) {
          editorManager.closeFile(file, false, closeAllCopies = true)
        }
      }

      // Reopen in the client that owned the diff. The reopen reads the host setting through showDiffFile.
      for ((clientId, files) in filesByClient) {
        ClientId.withExplicitClientId(clientId).use {
          val (toFocus, theRest) = files.headTail()
          for (file in theRest) {
            diffEditorManager.showDiffFile(file, false)
          }
          diffEditorManager.showDiffFile(toFocus, true)
        }
      }
    }
  }
}

/**
 * Toggle option on drag-n-drop
 */
@Suppress("RemoveRedundantQualifierName")
internal class DiffInWindowDndListener : FileOpenedSyncListener {
  override fun fileOpenedSync(editorManager: FileEditorManager, file: VirtualFile, editorsWithProviders: List<FileEditorWithProvider>) {
    if (file !is DiffContentVirtualFile) return
    if (editorManager !is FileEditorManagerImpl) return
    if (DiffEditorTabFilesUtil.isForceOpeningsInNewWindow(file)) return

    // flag is not properly set for async editor opening
    //if (file.getUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN) != true) return

    val openedFileEditors = editorsWithProviders.map { it.fileEditor }
    val window = editorManager.windows.find { editorWindow ->
      editorWindow.allComposites.any { editorComposite ->
        editorComposite.allEditors.any { editor ->
          openedFileEditors.contains(editor)
        }
      }
    } ?: return

    val isFileInEditor = !isSingletonEditorInWindow(window)
    if (DiffEditorTabFilesUtil.isDiffInEditor != isFileInEditor) {
      invokeLater(ModalityState.nonModal()) {
        DiffEditorTabFilesUtil.isDiffInEditor = isFileInEditor
      }
    }
  }
}

internal fun isSingletonEditorInWindow(window: EditorWindow): Boolean {
  return window.owner.isFloating && window.tabCount == 1
}
