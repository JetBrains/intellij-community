// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope

/**
 * Inline completion will be shown only if at least one [InlineCompletionProvider] is enabled and returns at least one proposal
 */
internal class InlineCompletionEditorListener(private val scope: CoroutineScope) : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    if (editor.project == null || editor !is EditorImpl || !editorTypeSupported(editor) || editor.project?.isDisposed != false) {
      return
    }
    InlineCompletion.install(editor, scope)
  }

  private fun editorTypeSupported(editor: Editor): Boolean {
    val isTest = editor.editorKind == EditorKind.UNTYPED && application.isUnitTestMode
    return editor.editorKind == EditorKind.MAIN_EDITOR || isTest
  }

  override fun editorReleased(event: EditorFactoryEvent) {
    InlineCompletion.remove(event.editor)
  }
}
