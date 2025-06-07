// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.listeners.InlineCompletionFocusListener
import com.intellij.codeInsight.inline.completion.listeners.InlineCompletionSelectionListener
import com.intellij.codeInsight.inline.completion.listeners.InlineEditorMouseListener
import com.intellij.codeInsight.inline.completion.listeners.typing.InlineCompletionDocumentListener
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.logs.TypingSpeedTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.observable.util.addKeyListener
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.application
import fleet.util.logging.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities

object InlineCompletion {
  private val KEY = Key.create<Pair<InlineCompletionHandler, Disposable>>("inline.completion.handler")
  private val LOG = logger<InlineCompletionHandler>()

  fun getHandlerOrNull(editor: Editor): InlineCompletionHandler? = editor.getUserData(KEY)?.first

  fun install(editor: EditorEx, scope: CoroutineScope) {
    if (!SwingUtilities.isEventDispatchThread()) {
      LOG.error("Inline Completion should be installed only in EDT. This error will be replaced with assertion.")
    }

    if (editor.isDisposed) {
      return
    }

    val currentHandler = getHandlerOrNull(editor)

    val disposable = Disposer.newDisposable("inline-completion").also {
      it.disposeWithEditorIfNeeded(editor)
    }

    val workingScope = scope.childScope(supervisor = !application.isUnitTestMode) // Completely fail only in tests
    val handler = InlineCompletionHandlerInitializer.initialize(editor, workingScope, disposable)
    if (handler == null) {
      workingScope.cancel()
      Disposer.dispose(disposable)
      LOG.trace { "[Inline Completion] No handler initializer is found for $editor." }
      return
    }

    if (currentHandler != null) {
      LOG.trace { "[Inline Completion] Handler is being replaced for $editor." }
      remove(editor)
    }

    editor.putUserData(KEY, handler to disposable)

    editor.document.addDocumentListener(InlineCompletionDocumentListener(editor), disposable)
    editor.addEditorMouseListener(InlineEditorMouseListener(), disposable)
    editor.addFocusListener(InlineCompletionFocusListener(), disposable)
    editor.contentComponent.addKeyListener(disposable, TypingSpeedTracker.KeyListener())
    editor.selectionModel.addSelectionListener(InlineCompletionSelectionListener(), disposable)

    application.messageBus.syncPublisher(InlineCompletionInstallListener.TOPIC).handlerInstalled(editor, handler)

    disposable.whenDisposed {
      workingScope.cancel()
    }

    LOG.trace { "[Inline Completion] Handler is installed for $editor." }
  }

  fun remove(editor: Editor) {
    val (handler, disposable) = editor.getUserData(KEY) ?: return

    handler.cancel(FinishType.EDITOR_REMOVED)
    Disposer.dispose(disposable)

    application.messageBus.syncPublisher(InlineCompletionInstallListener.TOPIC).handlerUninstalled(editor, handler)
    editor.putUserData(KEY, null)
    LOG.trace { "[Inline Completion] Handler is removed for $editor." }
  }

  private fun Disposable.disposeWithEditorIfNeeded(editor: Editor) {
    val isDisposed = AtomicReference(false)
    whenDisposed { isDisposed.set(true) }

    EditorUtil.disposeWithEditor(editor) {
      if (isDisposed.compareAndSet(false, true)) {
        Disposer.dispose(this@disposeWithEditorIfNeeded)
      }
    }
  }
}
