// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.audioCues

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.util.asDisposable
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.WeakReference
import kotlin.streams.asSequence
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

@ApiStatus.Internal
@OptIn(FlowPreview::class)
class EditorAudioCuesManager internal constructor(
  scope: CoroutineScope,
  private val settleDelay: Duration,
  private val editAdjacentSettleDelay: Duration,
) {
  constructor(scope: CoroutineScope) : this(
    scope,
    settleDelay = RegistryManager.getInstance()
      .intValue("ide.audio.cues.editor.settle.delay.ms", 50)
      .coerceAtLeast(0).milliseconds,
    editAdjacentSettleDelay = EDIT_ADJACENT_SETTLE_DELAY,
  )

  companion object {
    private val LAST_SETTLED_LINE = Key.create<Int>("editor.audio.cues.last.settled.line")
    private val LAST_DOCUMENT_CHANGE_AT = Key.create<TimeSource.Monotonic.ValueTimeMark>("editor.audio.cues.last.document.change.at")

    private val LOG = logger<EditorAudioCuesManager>()

    /** Settle delay next to an edit: cues are played only once no further edit or caret move has arrived for this long. */
    private val EDIT_ADJACENT_SETTLE_DELAY = 1000.milliseconds

    /** A caret move this soon after a document change belongs to that edit, so it settles for the edit-adjacent delay. */
    private val TYPING_WINDOW = 100.milliseconds

    private fun isSupportedEditorKind(editor: Editor): Boolean =
      editor.editorKind == EditorKind.MAIN_EDITOR || editor.editorKind == EditorKind.DIFF || editor.editorKind == EditorKind.CONSOLE
  }

  private val settings = service<AudioCuesSettings>()
  private val managerJob = scope.coroutineContext.job
  private val caretPositionRequests =
    MutableSharedFlow<CaretPositionRequest>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private var listenersDisposable: Disposable? = null

  private val caretListener = object : CaretListener {
    override fun caretPositionChanged(e: CaretEvent) {
      if (e.oldPosition == e.newPosition) return
      val editor = e.editor
      if (e.caret !== editor.caretModel.primaryCaret) return
      if (!isSupportedEditorKind(editor)) return
      if (CommandProcessor.getInstance().currentCommand == null || !AsyncEditorLoader.isEditorLoaded(editor)) {
        // a programmatic move (caret restore at file open, folding restore, ...) rebases the settled line
        // silently, so that a later intra-line user move does not replay line cues
        editor.putUserData(LAST_SETTLED_LINE, e.newPosition.line)
        return
      }
      val lastDocumentChangeAt = editor.document.getUserData(LAST_DOCUMENT_CHANGE_AT)
      val editAdjacent = lastDocumentChangeAt != null && lastDocumentChangeAt.elapsedNow() <= TYPING_WINDOW
      val offset = editor.logicalPositionToOffset(e.newPosition)
      caretPositionRequests.tryEmit(CaretPositionRequest(editor, e.newPosition.line, offset, editAdjacent))
    }
  }

  private val documentListener = object : BulkAwareDocumentListener {
    override fun documentChangedNonBulk(event: DocumentEvent) {
      handleDocumentChange(event.document, event.offset, event.offset + event.newLength)
    }

    override fun bulkUpdateFinished(document: Document) {
      document.putUserData(LAST_DOCUMENT_CHANGE_AT, TimeSource.Monotonic.markNow())
    }
  }

  internal fun handleDocumentChange(document: Document, changeStart: Int, changeEnd: Int) {
    document.putUserData(LAST_DOCUMENT_CHANGE_AT, TimeSource.Monotonic.markNow())
    if (!ApplicationManager.getApplication().isDispatchThread) return
    if (CommandProcessor.getInstance().currentCommand == null) return
    val changed = changeStart..changeEnd
    val (editor, offset) = EditorFactory.getInstance().editors(document).asSequence()
                             .filter { isSupportedEditorKind(it) && AsyncEditorLoader.isEditorLoaded(it) && UIUtil.hasFocus(it.contentComponent) }
                             .map { it to it.caretModel.primaryCaret.offset }
                             .firstOrNull { (_, offset) -> offset in changed } ?: return
    caretPositionRequests.tryEmit(CaretPositionRequest(editor, document.getLineNumber(offset), offset, editAdjacent = true))
  }

  init {
    RegistryManager.getInstance().get(AUDIO_CUES_ENABLED_REGISTRY_KEY).addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) = refreshAudioCuesState()
    }, scope)
    ScreenReader.addPropertyChangeListener(ScreenReader.SCREEN_READER_ACTIVE_PROPERTY, scope.asDisposable()) { refreshAudioCuesState() }
    managerJob.invokeOnCompletion { updateListenersState() }
    updateListenersState()

    scope.launch {
      caretPositionRequests
        .debounce { if (it.editAdjacent) editAdjacentSettleDelay else settleDelay }
        .collectLatest { runCatching { processCaretPosition(it) }.getOrLogException(LOG) }
    }
  }

  internal fun updateListenersState() {
    updateListenersDisposable()?.let(Disposer::dispose)
  }

  @Synchronized
  private fun updateListenersDisposable(): Disposable? {
    if (!managerJob.isActive || !settings.isEnabled) {
      return listenersDisposable.also { listenersDisposable = null }
    }
    if (listenersDisposable != null) return null

    val disposable = Disposer.newDisposable()
    val multicaster = EditorFactory.getInstance().eventMulticaster
    multicaster.addCaretListener(caretListener, disposable)
    multicaster.addDocumentListener(documentListener, disposable)
    listenersDisposable = disposable
    return null
  }

  private suspend fun processCaretPosition(request: CaretPositionRequest) {
    val editor = request.editorRef.get() ?: return
    val offset = request.offset
    val line = request.line
    val cues = readAction {
      if (editor.isDisposed || editor.caretModel.primaryCaret.offset != offset) null
      else detectCues(editor, line, offset)
    } ?: return
    val newLine = editor.getUserData(LAST_SETTLED_LINE) != line
    editor.putUserData(LAST_SETTLED_LINE, line)
    val scoped = if (newLine) cues else cues.filter { it.lineCounterpart != null }
    val toPlay = scoped.filterNot { detected ->
      val counterpart = detected.lineCounterpart ?: return@filterNot false
      scoped.any { it.cue === counterpart } && settings.isCueEnabled(counterpart)
    }
    if (toPlay.isNotEmpty()) AudioCuePlayer.getInstance().play(*toPlay.map { it.cue }.toTypedArray())
  }

  internal fun detectCues(editor: Editor, line: Int, caretOffset: Int): Set<EditorAudioCue> {
    if (editor.isDisposed) return emptySet()
    val document = editor.document
    if (line < 0 || line >= document.lineCount) return emptySet()
    if (caretOffset < 0 || caretOffset > document.textLength) return emptySet()

    val cues = mutableSetOf<EditorAudioCue>()
    EditorAudioCueDetector.EP_NAME.forEachExtensionSafe { cues += it.detect(editor, line, caretOffset) }
    return cues
  }

  private data class CaretPositionRequest(
    val editorRef: WeakReference<Editor>,
    val line: Int,
    val offset: Int,
    val editAdjacent: Boolean,
  ) {
    constructor(editor: Editor, line: Int, offset: Int, editAdjacent: Boolean) :
      this(WeakReference(editor), line, offset, editAdjacent)
  }
}
