// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.diff.DiffContext
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

internal val IS_DIFF_FILE_EDITOR: Key<Boolean> = Key.create("IS_DEFAULT_DIFF_EDITOR")

@ApiStatus.Internal
val CUSTOM_DIFF_ESCAPE_HANDLER: Key<AnAction> = Key.create("CUSTOM_DIFF_ESCAPE_HANDLER")

private class DefaultDiffFileEditorCustomizer : DiffRequestProcessorEditorCustomizer {
  override fun customize(file: VirtualFile, editor: FileEditor, context: DiffContext) {
    registerEscapeAction(editor)

    editor.putUserData(IS_DIFF_FILE_EDITOR, true)
    editor.putUserData(FileEditorManagerKeys.SINGLETON_EDITOR_IN_WINDOW, true)
    editor.putUserData(FileEditorManagerKeys.SHOW_NORTH_PANEL, false)
  }

  private fun registerEscapeAction(editor: FileEditor) {
    var escapeHandler = (editor.file as? DiffVirtualFileBase)?.createEscapeHandler()
    if (escapeHandler != null && escapeHandler !is DiffEditorEscapeAction) {
      escapeHandler = DiffEditorEscapeDelegatingAction(escapeHandler)
    }

    val defaultCloseAction = ActionManager.getInstance().getAction("CloseDiffEditor")
    if (escapeHandler != null) {
      editor.putUserData(CUSTOM_DIFF_ESCAPE_HANDLER, escapeHandler)
      escapeHandler.registerCustomShortcutSet(defaultCloseAction.shortcutSet, editor.component, null)
    }
    else {
      defaultCloseAction.registerCustomShortcutSet(editor.component, null)
    }
  }
}

private class DiffEditorEscapeDelegatingAction(delegate: AnAction) : AnActionWrapper(delegate), DiffEditorEscapeAction

private class CloseDiffEditorAction : DumbAwareAction(), DiffEditorEscapeAction, ActionRemoteBehaviorSpecification.Frontend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val fileEditor = getDiffEditor(e)
    if (fileEditor == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val escapeHandler = fileEditor.getUserData(CUSTOM_DIFF_ESCAPE_HANDLER)
    if (escapeHandler != null) {
      escapeHandler.update(e)
    }
    else {
      e.presentation.isEnabledAndVisible = true
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val fileEditor = getDiffEditor(e) ?: return

    val escapeHandler = fileEditor.getUserData(CUSTOM_DIFF_ESCAPE_HANDLER)
    if (escapeHandler != null) {
      escapeHandler.actionPerformed(e)
    }
    else {
      Disposer.dispose(fileEditor)
    }
  }

  private fun getDiffEditor(e: AnActionEvent): FileEditor? {
    // Can't use PlatformDataKeys.FILE_EDITOR because of FileEditorRule, it returns fake EditorWrapper for the in-diff Editor instances.
    val fileEditor = e.getData(EditorWindow.DATA_KEY)?.selectedComposite?.selectedEditor
                     ?: (if (e.place == ActionPlaces.ACTION_SEARCH) e.getData(PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR) else null)
                     ?: return null
    if (IS_DIFF_FILE_EDITOR.isIn(fileEditor)) {
      return fileEditor
    }
    return null
  }
}
