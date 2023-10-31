// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined.search

import com.intellij.diff.tools.combined.CombinedDiffBaseEditorWithSelectionHandler
import com.intellij.diff.tools.combined.CombinedDiffViewer
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler

private class CombinedDiffSearchEditorActionHandler(original: EditorActionHandler) : CombinedDiffBaseEditorWithSelectionHandler(original) {

  override fun doExecute(combined: CombinedDiffViewer, editor: Editor, caret: Caret?, dc: DataContext?) {
    val project = dc?.getData(CommonDataKeys.PROJECT) ?: return
    project.service<CombinedDiffSearchProvider>().installSearch(combined)
  }
}

private class SearchNextHandler(original: EditorActionHandler) : CombinedDiffBaseEditorWithSelectionHandler(original) {
  override fun doExecute(combined: CombinedDiffViewer, editor: Editor, caret: Caret?, dc: DataContext?) {
    if (dc == null) return

    invokeGoToOccurence(true, dc, combined)
  }
}

private class SearchPreviousHandler(original: EditorActionHandler) : CombinedDiffBaseEditorWithSelectionHandler(original) {
  override fun doExecute(combined: CombinedDiffViewer, editor: Editor, caret: Caret?, dc: DataContext?) {
    if (dc == null) return

    invokeGoToOccurence(false, dc, combined)
  }
}

private fun invokeGoToOccurence(forward: Boolean, handlerContext: DataContext, combined: CombinedDiffViewer) {
  val mainUI = combined.getMainUI()
  val searchDataProvider = mainUI.getSearchDataProvider() ?: return
  val context = CustomizedDataContext.create(handlerContext, searchDataProvider)
  val actionId = if (forward) "EditorSearchSession.NextOccurrenceAction" else "EditorSearchSession.PrevOccurrence"
  val prevOccurenceAction = ActionManager.getInstance().getAction(actionId)

  ActionUtil.invokeAction(prevOccurenceAction, context, ActionPlaces.KEYBOARD_SHORTCUT, null, null)
}
