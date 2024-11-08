// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.diff.editor.DiffEditorTabFilesManager.Companion.isDiffInEditor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
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
  override fun showDiffFile(diffFile: VirtualFile, focusEditor: Boolean): Array<out FileEditor> {
    val editorManager = FileEditorManager.getInstance(project) as FileEditorManagerImpl
    val openMode = if (isDiffInEditor) FileEditorManagerImpl.OpenMode.DEFAULT else FileEditorManagerImpl.OpenMode.NEW_WINDOW
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

internal abstract class MoveDiffEditorAction(private val openInNewWindow: Boolean) : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val editorWindow = e.getData(EditorWindow.DATA_KEY)
    val fileEditor = editorWindow?.selectedComposite?.selectedEditor
    if (fileEditor == null || !IS_DIFF_FILE_EDITOR.isIn(fileEditor)) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val isInWindow = isSingletonEditorInWindow(editorWindow)
    e.presentation.isEnabledAndVisible = isInWindow != openInNewWindow
  }

  override fun actionPerformed(e: AnActionEvent) {
    DiffEditorTabFilesManager.isDiffInEditor = !openInNewWindow
  }

  internal class ToEditor : MoveDiffEditorAction(false)
  internal class ToWindow : MoveDiffEditorAction(true)
}

internal class EditorTabDiffPreviewAdvancedSettingsListener : AdvancedSettingsChangeListener {
  override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
    if (id == DiffEditorTabFilesManager.SHOW_DIFF_IN_EDITOR_SETTING) {
      for (project in ProjectManager.getInstance().openProjects) {
        reopenDiffEditorsForFiles(project)
      }
    }
  }

  companion object {
    /**
     * Unlike [com.intellij.diff.editor.DiffEditorViewerFileEditor.reloadDiffEditorsForFiles], should not try to reopen tabs in-place.
     */
    private fun reopenDiffEditorsForFiles(project: Project) {
      val isOpenInNewWindow = DiffEditorTabFilesManager.isDiffInWindow

      val editorManager = FileEditorManager.getInstance(project) as? FileEditorManagerImpl ?: return
      val diffEditorManager = DiffEditorTabFilesManager.getInstance(project)

      val diffFiles = editorManager.windows
        .filter { window -> isSingletonEditorInWindow(window) != isOpenInNewWindow }
        .flatMap { window -> window.fileList }
        .filter { it is DiffContentVirtualFile }
        .distinct()
      if (diffFiles.isEmpty()) return

      for (file in diffFiles) {
        editorManager.closeFile(file, false, closeAllCopies = true)
      }

      val (toFocus, theRest) = diffFiles.headTail()
      for (file in theRest) {
        diffEditorManager.showDiffFile(file, false)
      }
      diffEditorManager.showDiffFile(toFocus, true)
    }
  }
}

/**
 * Toggle option on drag-n-drop
 */
internal class DiffInWindowDndListener : FileOpenedSyncListener {
  override fun fileOpenedSync(editorManager: FileEditorManager, file: VirtualFile, editorsWithProviders: List<FileEditorWithProvider>) {
    if (file !is DiffContentVirtualFile) return
    if (editorManager !is FileEditorManagerImpl) return

    // flag is not properly set for async editor opening
    //if (file.getUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN) != true) return

    val openedFileEditors = editorsWithProviders.map { it.fileEditor }
    val window = editorManager.windows.find { it.allComposites.any { it.allEditors.any { openedFileEditors.contains(it) } } } ?: return

    val isFileInEditor = !isSingletonEditorInWindow(window)
    if (DiffEditorTabFilesManager.isDiffInEditor != isFileInEditor) {
      invokeLater(ModalityState.nonModal()) {
        DiffEditorTabFilesManager.isDiffInEditor = isFileInEditor
      }
    }
  }
}

internal fun isSingletonEditorInWindow(window: EditorWindow): Boolean {
  return window.owner.isFloating && window.tabCount == 1
}
