// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actions.EnterAction
import com.intellij.openapi.editor.actions.StartNewLineAction
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresEdt

private val NEW_LINE_TRACKER_KEY = Key.create<InlineCompletionNewLineTracker>("inline.completion.newline.tracker")

private fun Editor.getTrackerOrNull(): InlineCompletionNewLineTracker? {
  return getUserData(NEW_LINE_TRACKER_KEY)
}

internal class InlineCompletionNewLineTracker {
  private var isInsideAction = false

  @RequiresEdt
  fun actionStarted() {
    isInsideAction = true
  }

  @RequiresEdt
  fun actionFinished() {
    isInsideAction = false
  }

  companion object {

    /**
     * Whether IDE currently performs an insertion of new line triggered by user.
     */
    fun isNewLineInsertion(editor: Editor): Boolean {
      return editor.getTrackerOrNull()?.isInsideAction ?: false
    }
  }
}

private class InlineCompletionNewLineAnActionListener : AnActionListener {
  override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
    if (action.isNewLineAction()) {
      event.getEditor()?.getTrackerOrNull()?.actionStarted()
    }
  }

  override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
    if (action.isNewLineAction()) {
      event.getEditor()?.getTrackerOrNull()?.actionFinished()
    }
  }

  private fun AnAction.isNewLineAction(): Boolean {
    // TODO support StartNewLineBeforeAction but is has problems with caret position
    return this is EnterAction || this is StartNewLineAction
  }

  private fun AnActionEvent.getEditor(): Editor? {
    return CommonDataKeys.EDITOR.getData(dataContext)
  }
}

private class InlineCompletionNewLineEditorListener : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    event.editor.putUserData(NEW_LINE_TRACKER_KEY, InlineCompletionNewLineTracker())
  }

  override fun editorReleased(event: EditorFactoryEvent) {
    event.editor.putUserData(NEW_LINE_TRACKER_KEY, null)
  }
}
