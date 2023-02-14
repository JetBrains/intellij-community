// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.diff

import com.intellij.collaboration.messages.CollaborationToolsBundle.messagePointer
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.CommonProcessors

abstract class CreateDiffCommentAction : DumbAwareAction(messagePointer("action.CodeReview.CreateDiffComment.text")) {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isActive(e) && isEnabledAndVisible(e)
  }

  protected abstract fun isActive(e: AnActionEvent): Boolean

  private fun isEnabledAndVisible(e: AnActionEvent): Boolean {
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return false
    return findRendererActionUnderCaret(editor) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getRequiredData(CommonDataKeys.EDITOR)
    val action = findRendererActionUnderCaret(editor) ?: return

    val scrollingModel = editor.scrollingModel
    scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    scrollingModel.runActionOnScrollingFinished {
      if (action is ActionGroup) {
        val point = editor.visualPositionToXY(editor.caretModel.visualPosition)
        ActionManager.getInstance().createActionPopupMenu(ActionPlaces.EDITOR_GUTTER_POPUP, action).component
          .show(editor.component, point.x, point.y - scrollingModel.verticalScrollOffset)
      }
      else ActionUtil.invokeAction(action, editor.component, ActionPlaces.EDITOR_GUTTER, e.inputEvent, null)
    }
  }

  private fun findRendererActionUnderCaret(editor: Editor): AnAction? {
    val markupModel = (editor as? EditorEx)?.markupModel ?: return null
    val logicalPosition = editor.caretModel.logicalPosition
    val line = logicalPosition.line
    val offset = editor.logicalPositionToOffset(logicalPosition)
    val findProcessor = object : CommonProcessors.FindProcessor<RangeHighlighterEx>() {
      override fun accept(t: RangeHighlighterEx): Boolean {
        val gutterIconRenderer = t.gutterIconRenderer
        return gutterIconRenderer is AddCommentGutterIconRenderer && gutterIconRenderer.line == line
      }
    }
    markupModel.processRangeHighlightersOverlappingWith(offset, offset, findProcessor)

    val renderer = findProcessor.foundValue?.gutterIconRenderer ?: return null
    return renderer.clickAction ?: renderer.popupMenuActions
  }
}