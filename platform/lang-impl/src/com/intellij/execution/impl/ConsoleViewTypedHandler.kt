// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.codeInsight.template.impl.editorActions.TypedActionHandlerBase
import com.intellij.execution.impl.ConsoleViewImpl.Companion.CONSOLE_VIEW_IN_EDITOR_VIEW
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.TypedActionHandler


internal class ConsoleViewTypedHandler(
  originalAction: TypedActionHandler,
) : TypedActionHandlerBase(originalAction) {

  override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
    val consoleView = editor.getUserData(CONSOLE_VIEW_IN_EDITOR_VIEW)
    if (consoleView == null || !consoleView.state.isRunning || consoleView.isViewer) {
      myOriginalHandler?.execute(editor, charTyped, dataContext)
      return
    }
    val text = charTyped.toString()
    consoleView.type(editor, text)
  }
}
