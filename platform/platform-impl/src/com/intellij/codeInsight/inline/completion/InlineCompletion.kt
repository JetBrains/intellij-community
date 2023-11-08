// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.listeners.InlineCompletionDocumentListener
import com.intellij.codeInsight.inline.completion.listeners.InlineCompletionFocusListener
import com.intellij.codeInsight.inline.completion.listeners.InlineEditorMouseListener
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.logs.TypingSpeedTracker
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.observable.util.addKeyListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.application
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope

object InlineCompletion {

  private val KEY = Key.create<InlineCompletionHandler>("inline.completion.handler")

  fun getHandlerOrNull(editor: Editor): InlineCompletionHandler? = editor.getUserData(KEY)

  fun install(editor: EditorEx, scope: CoroutineScope) {
    val disposable = Disposer.newDisposable("inline-completion").also {
      EditorUtil.disposeWithEditor(editor, it)
    }

    val workingScope = scope.childScope(supervisor = !application.isUnitTestMode) // Completely fail only in tests
    val handler = InlineCompletionHandler(workingScope, editor, disposable)
    editor.putUserData(KEY, handler)

    editor.document.addDocumentListener(InlineCompletionDocumentListener(editor), disposable)
    editor.addFocusListener(InlineCompletionFocusListener(), disposable)
    editor.addEditorMouseListener(InlineEditorMouseListener(), disposable)
    editor.contentComponent.addKeyListener(disposable, TypingSpeedTracker.KeyListener())
  }

  fun remove(editor: Editor) {
    editor.getUserData(KEY)?.cancel(FinishType.EDITOR_REMOVED)
    editor.putUserData(KEY, null)
  }
}
