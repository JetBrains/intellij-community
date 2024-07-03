// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.editor.CodeReviewCommentableEditorModel
import com.intellij.diff.util.LineRange
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.asSafely

internal class CodeReviewEditorNewCommentAction
  : DumbAwareAction(CollaborationToolsBundle.messagePointer("review.editor.action.add.comment.text"),
                    CollaborationToolsBundle.messagePointer("review.editor.action.add.comment.description")) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR).asSafely<EditorEx>() ?: run {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val model = editor.getUserData(CodeReviewCommentableEditorModel.KEY) ?: run {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isVisible = true

    if (model is CodeReviewCommentableEditorModel.WithMultilineComments) {
      val selectedRange = editor.getSelectedLinesRange()
      if (selectedRange != null) {
        e.presentation.text = CollaborationToolsBundle.message("review.editor.action.add.comment.multiline.text")
        e.presentation.description = CollaborationToolsBundle.message("review.editor.action.add.comment.multiline.description")
        e.presentation.isEnabled = model.canCreateComment(selectedRange)
        return
      }
    }

    val caretLine = editor.caretModel.logicalPosition.line.takeIf { it >= 0 }
    e.presentation.text = CollaborationToolsBundle.message("review.editor.action.add.comment.text")
    e.presentation.description = CollaborationToolsBundle.message("review.editor.action.add.comment.description")
    e.presentation.isEnabled = caretLine != null && model.canCreateComment(caretLine)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR) as? EditorEx ?: return
    val model = editor.getUserData(CodeReviewCommentableEditorModel.KEY) ?: return
    val scrollingModel = editor.scrollingModel

    if (model is CodeReviewCommentableEditorModel.WithMultilineComments) {
      val selectedRange = editor.getSelectedLinesRange()
      if (selectedRange != null) {
        scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        scrollingModel.runActionOnScrollingFinished {
          model.requestNewComment(selectedRange)
        }
        return
      }
    }

    val caretLine = editor.caretModel.logicalPosition.line.takeIf { it >= 0 } ?: return
    scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    scrollingModel.runActionOnScrollingFinished {
      model.requestNewComment(caretLine)
    }
  }

  private fun EditorEx.getSelectedLinesRange(): LineRange? {
    if (!selectionModel.hasSelection()) return null
    return with(selectionModel) {
      LineRange(editor.offsetToLogicalPosition(selectionStart).line, editor.offsetToLogicalPosition(selectionEnd).line)
    }
  }
}