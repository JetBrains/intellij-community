// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.grayText

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.grayText.GrayTextContext.Companion.getGrayTextContextOrNull
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
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * Gray text will be shown only if at least one [GrayTextProvider] is enabled and returns at least one proposal
 */
@ApiStatus.Experimental
class GrayTextEditorListener(private val scope: CoroutineScope) : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    if (editor.project == null || editor !is EditorImpl || editor.editorKind != EditorKind.MAIN_EDITOR) return

    GrayTextDocumentListener(editor, scope).listenForChanges()
  }
}

@Suppress("MemberVisibilityCanBePrivate")
@OptIn(FlowPreview::class)
@ApiStatus.Experimental
class GrayTextDocumentListener(private val editor: EditorImpl, private val scope: CoroutineScope) : DocumentListener, Disposable {
  private val handler = GrayTextHandler(scope)
  private var flow = MutableSharedFlow<GrayTextEvent>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  fun isEnabled(event: DocumentEvent): Boolean {
    return event.newFragment != CompletionUtil.DUMMY_IDENTIFIER && event.newLength >= 1
  }

  private fun documentChangedDebounced(grayTextEvent: GrayTextEvent) {
    val (event, providers) = grayTextEvent
    if (event.document.isInBulkUpdate) return

    // As PoC gray text does not support multiple providers
    // TODO: handle multiple providers
    handler.invoke(event, editor, providers.first())
  }

  fun listenForChanges() {
    Disposer.register(editor.disposable, this)
    editor.document.addDocumentListener(this, this)
    editor.putUserData(KEY, this)

    overrideCaretMove(editor)

    scope.launch(CoroutineName("gray.text.call")) {
      flow.debounce(minDelay)
        .collect(::documentChangedDebounced)
    }
  }

  override fun documentChanged(event: DocumentEvent) {
    if (GrayTextHandler.isMuted.get() || !isEnabled(event)) {
      return
    }

    val providers = GrayTextProvider.extensions().filter { it.isEnabled(event) }
                      .takeIf { it.isNotEmpty() } ?: return
    val grayTextEvent = GrayTextEvent(event, providers)

    if (ApplicationManager.getApplication().isUnitTestMode) {
      documentChangedDebounced(grayTextEvent)
    }
    else {
      flow.tryEmit(grayTextEvent)
    }
  }

  private fun overrideCaretMove(editor: EditorImpl) {
    val moveCaretAction = ActionUtil.getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT) ?: return

    DumbAwareAction.create {
      val toAct = if (editor.getGrayTextContextOrNull()?.isCurrentlyDisplayingInlays == true) {
        InsertGrayTextAction()
      }
      else {
        moveCaretAction
      }

      ActionUtil.performActionDumbAwareWithCallbacks(toAct, it)
    }.registerCustomShortcutSet(moveCaretAction.shortcutSet, editor.component)
  }

  override fun dispose() {
    editor.putUserData(KEY, null)
  }

  private data class GrayTextEvent(val event: DocumentEvent, val providers: List<GrayTextProvider>)

  companion object {
    private val KEY = Key.create<GrayTextDocumentListener>("gray.text.listener")

    val minDelay = Registry.get("gray.text.trigger.delay").asInteger().toLong()
  }
}