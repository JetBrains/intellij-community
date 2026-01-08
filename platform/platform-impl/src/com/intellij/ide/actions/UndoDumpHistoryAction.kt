// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.MessageDialogBuilder


internal class UndoDumpHistoryAction : UndoHistoryAction()

@Suppress("HardCodedStringLiteral", "DialogTitleCapitalization") // it is an internal action with ~1 user (me)
internal class UndoClearHistoryAction : UndoHistoryAction() {
  override fun perform(undoManager: UndoManagerImpl, editor: FileEditor?) {
    val confirmedClear = MessageDialogBuilder
      .yesNo(
        "Clear undo history",
        "Are you sure you want to clear undo history for ${editor}?\nThis action can't be undone!",
      )
      .yesText("Clear history")
      .noText("Cancel")
      .asWarning()
      .ask(undoManager.project)
    if (confirmedClear) {
      super.perform(undoManager, editor)
      undoManager.clearStacks(editor)
      super.perform(undoManager, editor)
      Notification(
        "Undo/redo",
        "Undo history is cleared",
        "for ${editor}",
        NotificationType.WARNING,
      ).notify(undoManager.project)
    }
  }
}

internal abstract class UndoHistoryAction : DumbAwareAction() {

  init {
    isEnabledInModalContext = true
  }

  override fun update(e: AnActionEvent) {
    e.presentation.setEnabled(true)
  }

  override fun actionPerformed(e: AnActionEvent) {
    dumpHistory(e)
  }

  private fun dumpHistory(e: AnActionEvent) {
    val dataContext: DataContext = e.dataContext
    val editor: FileEditor? = PlatformCoreDataKeys.FILE_EDITOR.getData(dataContext)
    val undoManager: UndoManager? = UndoRedoAction.getUndoManager(editor, dataContext, false, false)
    LOG.warn("${undoManager ?: "null undo manager"}")
    if (undoManager is UndoManagerImpl) {
      perform(undoManager, editor)
    }
  }

  protected open fun perform(undoManager: UndoManagerImpl, editor: FileEditor?) {
    LOG.warn(undoManager.dumpState(editor, "triggered by ${this::class.simpleName}"))
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  companion object {
    private val LOG: Logger = logger<UndoDumpHistoryAction>()
  }
}
