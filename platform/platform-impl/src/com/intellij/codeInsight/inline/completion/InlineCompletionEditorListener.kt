// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.inline.completion.InlineCompletionContext.Companion.getInlineCompletionContextOrNull
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus

/**
 * Inline completion will be shown only if at least one [InlineCompletionProvider] is enabled and returns at least one proposal
 */
@ApiStatus.Experimental
class InlineCompletionEditorListener(private val scope: CoroutineScope) : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    if (editor.project == null || editor !is EditorImpl || editor.editorKind != EditorKind.MAIN_EDITOR) return

    InlineCompletionDocumentListener(editor, scope).listenForChanges()
  }
}

@Suppress("MemberVisibilityCanBePrivate")
@OptIn(FlowPreview::class)
@ApiStatus.Experimental
class InlineCompletionDocumentListener(private val editor: EditorImpl, private val scope: CoroutineScope) : DocumentListener, Disposable {
  private val handler = InlineCompletionHandler(scope)
  private var flow = MutableSharedFlow<InlineCompletionEvent>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private var jobCall: Job? = null

  fun isEnabled(event: DocumentEvent): Boolean {
    return event.newFragment != CompletionUtil.DUMMY_IDENTIFIER && event.newLength >= 1
  }

  private fun documentChangedDebounced(inlineCompletionEvent: InlineCompletionEvent) {
    val (event, providers) = inlineCompletionEvent
    if (event.document.isInBulkUpdate) return

    // As PoC inline completion does not support multiple providers
    // TODO: handle multiple providers
    handler.invoke(event, editor, providers.first())
  }

  fun listenForChanges() {
    Disposer.register(editor.disposable, this)
    editor.document.addDocumentListener(this, this)
    editor.putUserData(KEY, this)

    overrideCaretMove(editor)

    jobCall = scope.launch(CoroutineName("inline.completion.call")) {
      flow.debounce(minDelay)
        .collect(::documentChangedDebounced)
    }
  }

  override fun documentChanged(event: DocumentEvent) {
    if (InlineCompletionHandler.isMuted.get() || !isEnabled(event)) {
      return
    }

    val providers = InlineCompletionProvider.extensions().filter { it.isEnabled(event) }
                      .takeIf { it.isNotEmpty() } ?: return
    val inlineCompletionEvent = InlineCompletionEvent(event, providers)

    if (ApplicationManager.getApplication().isUnitTestMode) {
      documentChangedDebounced(inlineCompletionEvent)
    }
    else {
      flow.tryEmit(inlineCompletionEvent)
    }
  }

  private fun overrideCaretMove(editor: EditorImpl) {
    val moveCaretAction = ActionUtil.getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT) ?: return

    DumbAwareAction.create {
      val toAct = if (editor.getInlineCompletionContextOrNull()?.isCurrentlyDisplayingInlays == true) {
        InsertInlineCompletionAction()
      }
      else {
        moveCaretAction
      }

      ActionUtil.performActionDumbAwareWithCallbacks(toAct, it)
    }.registerCustomShortcutSet(moveCaretAction.shortcutSet, editor.component)
  }

  override fun dispose() {
    editor.putUserData(KEY, null)
    jobCall?.cancel()
  }

  private data class InlineCompletionEvent(val event: DocumentEvent, val providers: List<InlineCompletionProvider>)

  companion object {
    private val KEY = Key.create<InlineCompletionDocumentListener>("inline.completion.listener")

    val minDelay = Registry.get("inline.completion.trigger.delay").asInteger().toLong()
  }
}
