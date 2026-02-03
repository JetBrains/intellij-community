// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions

import com.intellij.execution.console.LanguageConsoleView
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.wm.IdeFocusManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class LanguageConsolePasteHandler(val originalHandler: EditorActionHandler) : EditorActionHandler() {

  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    if (editor.editorKind == EditorKind.CONSOLE && editor.isViewer && dataContext != null) {
      editor.getUserData(LanguageConsoleView.EXECUTION_EDITOR_KEY)?.let { executionEditor ->
        if (executionEditor.isConsoleEditorEnabled) {
          executionEditor.editor.pasteProvider.performPaste(dataContext)
          IdeFocusManager.getGlobalInstance().requestFocus(executionEditor.editor.contentComponent, true)
          return
        }
      }
    }
    return originalHandler.execute(editor, caret, dataContext)
  }
}