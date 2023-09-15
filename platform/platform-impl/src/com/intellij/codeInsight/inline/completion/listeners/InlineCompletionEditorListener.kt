// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners

import com.intellij.codeInsight.inline.completion.InlineCompletionHandler
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.util.application
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * Inline completion will be shown only if at least one [InlineCompletionProvider] is enabled and returns at least one proposal
 */
@ApiStatus.Experimental
class InlineCompletionEditorListener(private val scope: CoroutineScope) : EditorFactoryListener {
  private val editorMouseListener = InlineEditorMouseListener()

  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    if (editor.project == null || editor !is EditorImpl || !editorTypeSupported(editor) || editor.project?.isDisposed != false) return

    val disposable = Disposer.newDisposable("inline-completion").also {
      EditorUtil.disposeWithEditor(editor, it)
    }

    val handler = InlineCompletionHandler(scope.childScope())
    editor.putUserData(InlineCompletionHandler.KEY, handler)
    val docListener = InlineCompletionDocumentListener(editor)
    val caretListener = InlineCaretListener()

    editor.document.addDocumentListener(docListener, disposable)
    editor.addEditorMouseListener(editorMouseListener, disposable)
    editor.caretModel.addCaretListener(caretListener, disposable)
  }

  private fun editorTypeSupported(editor: Editor): Boolean {
    val isTest = editor.editorKind == EditorKind.UNTYPED && application.isUnitTestMode
    return editor.editorKind == EditorKind.MAIN_EDITOR || isTest
  }

  override fun editorReleased(event: EditorFactoryEvent) {
    event.editor.getUserData(InlineCompletionHandler.KEY)?.cancel(event.editor)
    event.editor.putUserData(InlineCompletionHandler.KEY, null)
  }
}
// [root]
// InlineLifecycleActionTest
// test lifecycle ARSCNcH
// InlineLifecycleLookupTest
// test lifecycle LRSCTH
// test lifecycle LRSCNcH
