// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.impl.CaretImpl
import com.intellij.openapi.project.DumbAware
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SelectWordAtCurrentCaretAction : EditorAction(DefaultHandler()), DumbAware {
  init {
    setInjectedContext(true)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.setEnabledAndVisible(false)
  }

  override fun getEditor(dataContext: DataContext): Editor? {
    return TextComponentEditorAction.getEditorFromContext(dataContext)
  }

  private class DefaultHandler : EditorActionHandler() {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
      val caret = caret ?: editor.caretModel.currentCaret

      val caretModel = editor.getCaretModel()
      val honorCamelWords = CaretImpl.HONOR_CAMEL_WORDS.getData(dataContext) ?: true
      caretModel.runBatchCaretOperation { selectWordAtCaret(editor, caret, dataContext, honorCamelWords) }
    }

    private fun selectWordAtCaret(editor: Editor, caret: Caret, dataContext: DataContext, honorCamelWordsSettings: Boolean) {
      caret.removeSelection()
      val settings: EditorSettings = editor.getSettings()
      val camelTemp = settings.isCamelWords()

      val needOverrideSetting = camelTemp && !honorCamelWordsSettings
      if (needOverrideSetting) {
        settings.setCamelWords(false)
      }
      try {
        val handler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)
        handler.execute(editor, caret, dataContext)
      }
      finally {
        if (needOverrideSetting) {
          settings.resetCamelWords()
        }
      }
    }
  }
}
