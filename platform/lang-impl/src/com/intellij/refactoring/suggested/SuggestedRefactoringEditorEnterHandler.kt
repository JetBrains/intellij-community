// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.suggested

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.project.Project

private class SuggestedRefactoringEditorEnterHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {
  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    val project = editor.project
    return isSuggestedRefactoringHintShown(project, editor) || originalHandler.isEnabled(editor, caret, dataContext)
  }

  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    val project = editor.project
    if (isSuggestedRefactoringHintShown(project, editor)) {
      performSuggestedRefactoring(project!!,
                                  editor,
                                  null,
                                  null,
                                  showReviewBalloon = true,
                                  ActionPlaces.KEYBOARD_SHORTCUT)
    }
    else {
      originalHandler.execute(editor, caret, dataContext)
    }
  }
}

internal fun isSuggestedRefactoringHintShown(project: Project?, editor: Editor): Boolean = project != null &&
                                                                                           isSuggestedRefactoringEditorHintEnabled() &&
                                                                                           SuggestedRefactoringProviderImpl.getInstance(project).availabilityIndicator.isHintShown(editor)