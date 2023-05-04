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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
abstract class GrayTextEditorListener(private val scope: CoroutineScope) : EditorFactoryListener {
  open fun initDocumentListener(editor: EditorImpl) = GrayTextDocumentListener(editor, scope)

  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    if (editor.project == null || editor !is EditorImpl || editor.editorKind != EditorKind.MAIN_EDITOR) return

    initDocumentListener(editor).listenForChanges()
    overrideCaretMove(editor)
  }

  private fun overrideCaretMove(editor: EditorImpl) {
    val moveCaretAction = ActionUtil.getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT) ?: return

    DumbAwareAction.create {
      val toAct = if (editor.getGrayTextContextOrNull()?.isCurrentlyDisplayingInlays == true) {
        AcceptGrayTextAction()
      }
      else {
        moveCaretAction
      }

      ActionUtil.performActionDumbAwareWithCallbacks(toAct, it)
    }.registerCustomShortcutSet(moveCaretAction.shortcutSet, editor.component)
  }
}

@Suppress("MemberVisibilityCanBePrivate")
@OptIn(FlowPreview::class)
@ApiStatus.Internal
open class GrayTextDocumentListener(
  val editor: EditorImpl,
  private val scope: CoroutineScope,
) : DocumentListener, Disposable {
  private val handler = GrayTextHandler(scope)
  private var flow = MutableSharedFlow<DocumentEvent>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  open val minimalDelayMillis = 300.milliseconds

  open fun isEnabled(event: DocumentEvent): Boolean {
    return event.newFragment != CompletionUtil.DUMMY_IDENTIFIER && event.newLength >= 1
  }

  open fun initProvider(event: DocumentEvent): GrayTextProvider = GrayTextProvider.DUMMY

  open fun documentChangedDebounced(event: DocumentEvent) {
    if (event.document.isInBulkUpdate) return

    val provider = initProvider(event)
    handler.invoke(event, editor, provider)
  }

  fun listenForChanges() {
    Disposer.register(editor.disposable, this)
    editor.document.addDocumentListener(this, this)
    editor.putUserData(KEY, this)

    scope.launch(CoroutineName("full-line GrayText call")) {
      flow.debounce(minimalDelayMillis)
        .collect(::documentChangedDebounced)
    }
  }

  override fun documentChanged(event: DocumentEvent) {
    if (GrayTextHandler.isMuted.get() || !isEnabled(event)) {
      return
    }

    if (ApplicationManager.getApplication().isUnitTestMode) {
      documentChangedDebounced(event)
    }
    else {
      flow.tryEmit(event)
    }
  }

  override fun dispose() {
    editor.putUserData(KEY, null)
  }

  companion object {
    private val KEY = Key.create<GrayTextDocumentListener>("completion.gray.text.listener")
  }
}
