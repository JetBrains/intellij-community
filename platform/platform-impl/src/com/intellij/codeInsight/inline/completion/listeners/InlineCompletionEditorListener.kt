// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners

import com.intellij.codeInsight.inline.completion.InlineCompletionHandler
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * Inline completion will be shown only if at least one [InlineCompletionProvider] is enabled and returns at least one proposal
 */
@ApiStatus.Experimental
class InlineCompletionEditorListener(scope: CoroutineScope) : EditorFactoryListener {
  private val focusListener = InlineCompletionFocusListener()

  private val handler = InlineCompletionHandler(scope)

  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    if (editor.project == null || editor !is EditorImpl || editor.editorKind != EditorKind.MAIN_EDITOR || editor.project?.isDisposed != false) return

    val disposable = Disposer.newDisposable("inline-completion").also {
      EditorUtil.disposeWithEditor(editor, it)
    }

    editor.putUserData(InlineCompletionHandler.KEY, handler)
    val docListener = InlineCompletionDocumentListener(editor)
    val caretListener = InlineCaretListener(editor)

    editor.document.addDocumentListener(docListener, disposable)
    editor.addFocusListener(focusListener, disposable)
    editor.caretModel.addCaretListener(caretListener, disposable)
  }

  override fun editorReleased(event: EditorFactoryEvent) {
    event.editor.putUserData(InlineCompletionHandler.KEY, null)
  }
}


