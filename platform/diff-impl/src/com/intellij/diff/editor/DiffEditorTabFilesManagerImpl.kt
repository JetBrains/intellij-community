// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.containers.headTail

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

    val isInWindow = editorWindow.owner.isFloating && editorWindow.tabCount == 1
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
      val editorManager = FileEditorManager.getInstance(project) as? FileEditorManagerImpl ?: return
      val diffEditorManager = DiffEditorTabFilesManager.getInstance(project)

      val diffFiles = editorManager.openFiles
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
