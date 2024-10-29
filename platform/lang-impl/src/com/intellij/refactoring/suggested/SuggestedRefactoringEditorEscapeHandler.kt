// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.suggested

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler

private class SuggestedRefactoringEditorEscapeHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {
  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    val project = editor.project
    return isSuggestedRefactoringHintShown(project, editor) || originalHandler.isEnabled(editor, caret, dataContext)
  }

  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    val project = editor.project
    if (isSuggestedRefactoringHintShown(project, editor)) {
      SuggestedRefactoringProviderImpl.getInstance(project!!).suppressForCurrentDeclaration()
    }
    originalHandler.execute(editor, caret, dataContext)
  }
}