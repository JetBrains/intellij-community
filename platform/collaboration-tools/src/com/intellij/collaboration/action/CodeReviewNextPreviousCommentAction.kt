// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ActionPresentationInstantiatedInCtor")

package com.intellij.collaboration.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.editor.CodeReviewNavigableEditorViewModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorKeys
import com.intellij.collaboration.ui.codereview.timeline.thread.CodeReviewTrackableItemViewModel
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.wm.IdeFocusManager
import org.jetbrains.annotations.ApiStatus

internal class CodeReviewPreviousCommentAction : CodeReviewNextPreviousCommentAction(
  text = CollaborationToolsBundle.message("action.CodeReview.PreviousComment.text"),
  description = CollaborationToolsBundle.message("action.CodeReview.PreviousComment.description"),
  canGotoThreadComment = { threadId -> canGotoPreviousComment(threadId) },
  canGotoLineComment = { line -> canGotoPreviousComment(line) },
  gotoThreadComment = { threadId -> gotoPreviousComment(threadId) },
  gotoLineComment = { line -> gotoPreviousComment(line) },
)

internal class CodeReviewNextCommentAction : CodeReviewNextPreviousCommentAction(
  text = CollaborationToolsBundle.message("action.CodeReview.NextComment.text"),
  description = CollaborationToolsBundle.message("action.CodeReview.NextComment.description"),
  canGotoThreadComment = { threadId -> canGotoNextComment(threadId) },
  canGotoLineComment = { line -> canGotoNextComment(line) },
  gotoThreadComment = { threadId -> gotoNextComment(threadId) },
  gotoLineComment = { line -> gotoNextComment(line) },
)

internal abstract class CodeReviewNextPreviousCommentAction(
  text: @NlsActions.ActionText String,
  description: @NlsActions.ActionDescription String,
  private val canGotoThreadComment: CodeReviewNavigableEditorViewModel.(String) -> Boolean,
  private val canGotoLineComment: CodeReviewNavigableEditorViewModel.(Int) -> Boolean,
  private val gotoThreadComment: CodeReviewNavigableEditorViewModel.(String) -> Unit,
  private val gotoLineComment: CodeReviewNavigableEditorViewModel.(Int) -> Unit,
) : DumbAwareAction(text, description, null), ActionPromoter {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return

    val (editor, editorModel) = getEditorModel(e)
    if (editor == null || editorModel == null || !editorModel.canNavigate) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val focused = findFocusedThreadId(project)
    e.presentation.isEnabled = if (focused != null) {
      editorModel.canGotoThreadComment(focused)
    }
    else {
      val editorLine = editor.caretModel.logicalPosition.line // zero-index
      editorModel.canGotoLineComment(editorLine)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val (editor, editorModel) = getEditorModel(e)
    if (editor == null || editorModel == null || !editorModel.canNavigate) return

    val focused = findFocusedThreadId(project)
    if (focused != null) {
      editorModel.gotoThreadComment(focused)
    }
    else {
      val editorLine = editor.caretModel.logicalPosition.line // zero-index
      editorModel.gotoLineComment(editorLine)
    }
  }

  override fun suppress(actions: List<AnAction>, context: DataContext): List<AnAction>? {
    if (context.getData(CodeReviewTrackableItemViewModel.TRACKABLE_ITEM_KEY) != null) {
      return actions.filterNot { it == this }
    }
    return null
  }

  private fun getEditorModel(e: AnActionEvent): Pair<Editor?, CodeReviewNavigableEditorViewModel?> {
    val currentEditor = e.getData(DiffDataKeys.CURRENT_EDITOR) ?: e.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE)
    currentEditor?.getUserData(CodeReviewNavigableEditorViewModel.KEY)?.let { navigatableModel ->
      return Pair(currentEditor, navigatableModel)
    }

    val navigatableEditor = e.getData(CodeReviewEditorKeys.NAVIGABLE_EDITOR_KEY)
    return Pair(navigatableEditor, navigatableEditor?.getUserData(CodeReviewNavigableEditorViewModel.KEY))
  }
}

@ApiStatus.Internal
fun findFocusedThreadId(project: Project): String? {
  val focusedComponent = IdeFocusManager.getInstance(project).focusOwner ?: return null
  val focusedData = DataManager.getInstance().getDataContext(focusedComponent)
  return focusedData.getData(CodeReviewTrackableItemViewModel.TRACKABLE_ITEM_KEY)?.trackingId
}
