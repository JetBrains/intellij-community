// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.editor

import andel.intervals.AnchorStorage
import andel.operation.*
import andel.text.Text
import andel.text.TextRange
import fleet.util.UID
import fleet.util.logging.KLoggers
import fleet.util.openmap.BoundedOpenMap
import fleet.util.openmap.MutableOpenMap
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.createCoroutineUnintercepted
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.time.TimeSource

/**
 * An isolated editor given to each caret. See [MutableEditor.runForEachCaret].
 */
@RestrictsSuspension
interface TransientEditor : MutableEditor {
  val originalEditor: MutableEditor

  /**
   * An isolated document associated with that editor.
   */
  override val document: TransientDocument

  /**
   * Apply all collected edits and caret movements to the original editor.
   *
   * Suspends until all carets do the same OR finish their action execution.
   */
  suspend fun commitEdits()

  fun checkHistoryMarkRequested(): Boolean

  val currentCaret: Caret
    get() = multiCaret.carets.single()
}

interface TransientDocument : MutableDocument {
  val accumulatedChanges: Operation

  fun applyOthersChangesAndReset(operation: NewOffsetProvider, afterText: Text)
}

internal interface EditListener {
  fun edit(newOffsetProvider: NewOffsetProvider)

  fun edit(operation: Operation)
}


/**
 * An entry point for simple and performant multi-caret actions.
 *
 * Runs the given block [f] for each caret in [MutableEditor.multiCaret] in a special environment:
 *
 * * Own [TransientEditor] is given to each caret. They do not transmit edits to original editor.
 *   At the same time they do not have direct access to components, e.g. AST, they need to ask original editor for them.
 *
 * * Each [TransientEditor] is able to [commit][TransientEditor.commitEdits] changes to the original editor.
 *   After that they can get actual AST.
 *
 * * The order of execution is the following:
 *   1. Execute [f] for the first caret until first [TransientEditor.commitEdits].
 *   2. Execute [f] for the second caret until first [TransientEditor.commitEdits].
 *
 *   ...
 *
 *   n. Execute [f] for the last caret until first [TransientEditor.commitEdits].
 *
 *   n+1. Apply changes from all `n` transient editors to the original editor.
 *
 *   n+2. Execute [f] for the first caret from last suspension point to the next [TransientEditor.commitEdits]
 *
 *   ... and so on.
 */
fun MutableEditor.runForEachCaret(f: suspend TransientEditor.() -> Unit) {
  if (this is TransientEditor) {
    f.startCoroutineUninterceptedOrReturn(this, object : Continuation<Unit> {
      override val context: CoroutineContext = EmptyCoroutineContext

      override fun resumeWith(result: Result<Unit>) {
        result.getOrThrow()
      }
    })
  }
  else {
    val orchestrator = PerCaretOrchestrator()
    orchestrator.forEachCaret(this, f)
  }
}

private fun MutableEditor.toTransient(syncEditor: suspend (TransientEditor) -> Unit): TransientEditorImpl {
  val original = this
  return TransientEditorImpl(original, syncEditor)
}

private class PerCaretOrchestrator {
  companion object {
    val logger = KLoggers.logger(PerCaretOrchestrator::class)
  }

  val continuations: MutableMap<TransientEditor, Continuation<Unit>> = HashMap()

  fun forEachCaret(originalEditor: MutableEditor, f: suspend TransientEditor.() -> Unit) {
    val editors: List<TransientEditorImpl> = originalEditor.multiCaret.carets.map { caret ->
      originalEditor.caretsSubsetView(listOf(caret.caretId)).toTransient(this::syncEditor)
    }

    val exceptions = ArrayList<Throwable>()

    editors.forEach { editor ->
      continuations[editor] = f.createCoroutineUnintercepted(editor, object : Continuation<Unit> {
        override val context: CoroutineContext = EmptyCoroutineContext

        override fun resumeWith(result: Result<Unit>) {
          result.onFailure {
            exceptions.add(it)
          }
        }
      })
    }

    while (continuations.isNotEmpty()) {
      val currentSet = ArrayList(continuations.values)
      continuations.clear()
      val initialTime = TimeSource.Monotonic.markNow()
      logger.trace { "Start iteration. Millis=${initialTime.elapsedNow().inWholeMilliseconds}" }
      currentSet.forEach {
        it.resume(Unit)
      }
      logger.trace { "Collected iteration. Millis=${initialTime.elapsedNow().inWholeMilliseconds}" }

      val accumulatedChanges = editors.map { it.document.accumulatedChanges }
      val operationsTreap = buildOperationTreap(accumulatedChanges)
      val totalOperation = composeAll(accumulatedChanges)
      if (totalOperation.isNotIdentity()) {
        originalEditor.document.edit(totalOperation)
        val afterText = originalEditor.document.text

        editors.forEach { editor ->
          val thisOperation = editor.document.accumulatedChanges
          operationsTreap.addRemoveOperation(thisOperation, isAdd = false)
          try {
            editor.document.applyOthersChangesAndReset(operationsTreap.asNewOffsetProvider(), afterText)
          }
          finally {
            operationsTreap.addRemoveOperation(thisOperation, isAdd = true)
          }
        }
      }

      val carets = editors.flatMap { it.multiCaret.carets }
      originalEditor.moveCarets(carets)
      val anchorsSize = editors.sumOf { it.document.anchorsForDocument.size }
      val rangesSize = editors.sumOf { it.document.rangeMarkersForDocument.size }
      val anchors = ArrayList<AnchorId>(anchorsSize)
      val anchorOffsets = LongArray(anchorsSize)
      val rangeIds = ArrayList<RangeMarkerId>(rangesSize)
      val ranges = ArrayList<TextRange>(rangesSize)
      var anchorIndex = 0
      editors.forEach {
        it.document.anchorsForDocument.forEach { (id, offset) ->
          anchors.add(id)
          anchorOffsets[anchorIndex++] = offset
        }
        it.document.rangeMarkersForDocument.forEach { (id, textRange) ->
          rangeIds.add(id)
          ranges.add(textRange)
        }
      }
      originalEditor.document.batchUpdateAnchors(anchors, anchorOffsets, rangeIds, ranges)
      // this clumsy check is very intentional. We need to reset flag in all editors.
      if (editors.count { it.checkHistoryMarkRequested() } > 0) {
        originalEditor.addHistoryPlace()
      }

      logger.trace { "Applied iteration. Millis=${initialTime.elapsedNow().inWholeMilliseconds}" }
    }

    if (exceptions.isNotEmpty()) {
      logger.error { "Got exceptions during `forEachCaret`. Suppressed: ${exceptions.drop(1).size}}" }
      throw exceptions.first()
    }
  }

  suspend fun syncEditor(editor: TransientEditor) {
    return suspendCoroutineUninterceptedOrReturn { c ->
      continuations[editor] = c
      COROUTINE_SUSPENDED
    }
  }
}

private class TransientEditorImpl(private val original: MutableEditor,
                                  private val syncEditor: suspend (TransientEditor) -> Unit) : TransientEditor, MutableEditor by original {
  override val originalEditor: MutableEditor
    get() = original

  override val document: TransientDocumentImpl = original.document.toTransient().also {
    it.addEditListener(object : EditListener {
      override fun edit(newOffsetProvider: NewOffsetProvider) {
        multiCaret.multiCaretData = multiCaret.multiCaretData.edit(newOffsetProvider)
      }

      override fun edit(operation: Operation) {
        multiCaret.multiCaretData = multiCaret.multiCaretData.edit(operation)
      }
    })
  }

  override val multiCaret: SimpleMutableMultiCaret = SimpleMutableMultiCaret(document,
    SimpleMultiCaretState(
      MultiCaretData().addCarets(original.carets), original.primaryCaret.caretId, original.multiCaret.meta))

  override suspend fun commitEdits() {
    if (document.accumulatedChanges.isIdentity()) return

    syncEditor(this)
  }

  private var historyAddRequested: Boolean = false

  override fun checkHistoryMarkRequested(): Boolean {
    return historyAddRequested.also {
      historyAddRequested = false
    }
  }

  override fun addHistoryPlace() {
    historyAddRequested = true
  }
}

private fun MutableDocument.toTransient(): TransientDocumentImpl {
  return TransientDocumentImpl(this)
}

private class TransientDocumentImpl(val original: MutableDocument) : TransientDocument {
  private var intermediateText: Text = original.text

  private var documentIntermediateAnchorStorage: AnchorStorage = AnchorStorage.empty()
  private var intermediateAnchorStorage: AnchorStorage = AnchorStorage.empty()

  private val editListeners: MutableList<EditListener> = ArrayList()

  private val anchorIds = HashSet<UID>()

  private val rangeMarkerIds = HashSet<UID>()

  override var accumulatedChanges: Operation = Operation.empty()
    private set

  override val components: BoundedOpenMap<MutableDocument, DocumentComponent>
    get() = original.components // todo maybe we need a proxy checking pending changes

  override val meta: MutableOpenMap<DocumentMeta>
    get() = original.meta

  val anchorsForDocument: List<Pair<AnchorId, Long>>
    get() = documentIntermediateAnchorStorage.intervals.asIterable().filter { it.id in anchorIds }.map { AnchorId(it.id) to it.from }

  val rangeMarkersForDocument: List<Pair<RangeMarkerId, TextRange>>
    get() = documentIntermediateAnchorStorage.intervals.asIterable().filter { it.id in rangeMarkerIds }.map { RangeMarkerId(it.id) to TextRange(it.from, it.to) }

  fun addEditListener(listener: EditListener) {
    editListeners.add(listener)
  }

  override fun applyOthersChangesAndReset(newOffsetProvider: NewOffsetProvider, afterText: Text) {
    documentIntermediateAnchorStorage = documentIntermediateAnchorStorage.edit(newOffsetProvider)
    intermediateAnchorStorage = intermediateAnchorStorage.edit(newOffsetProvider)
    intermediateText = afterText
    accumulatedChanges = Operation.empty()

    editListeners.forEach { it.edit(newOffsetProvider) }
  }

  override fun edit(operation: Operation) {
    accumulatedChanges = accumulatedChanges.compose(operation)

    val before = intermediateText
    intermediateText = intermediateText.mutableView()
      .apply { edit(operation) }
      .text()
    documentIntermediateAnchorStorage = documentIntermediateAnchorStorage.edit(before, intermediateText, operation)
    intermediateAnchorStorage = intermediateAnchorStorage.edit(before, intermediateText, operation)

    editListeners.forEach { it.edit(operation) }
  }

  override fun createAnchor(offset: Long, lifetime: AnchorLifetime, sticky: Sticky): AnchorId {
    return AnchorId(UID.random()).also {
      when (lifetime) {
        AnchorLifetime.MUTATION -> intermediateAnchorStorage = intermediateAnchorStorage.addAnchor(it, offset, sticky)
        AnchorLifetime.DOCUMENT -> documentIntermediateAnchorStorage = documentIntermediateAnchorStorage.addAnchor(it, offset, sticky)
      }
      anchorIds.add(it.id)
    }
  }

  override fun removeAnchor(anchorId: AnchorId) {
    documentIntermediateAnchorStorage = documentIntermediateAnchorStorage.removeAnchor(anchorId)
    intermediateAnchorStorage = intermediateAnchorStorage.removeAnchor(anchorId)
    anchorIds.remove(anchorId.id)
  }

  override fun createRangeMarker(rangeStart: Long, rangeEnd: Long, lifetime: AnchorLifetime): RangeMarkerId {
    return RangeMarkerId(UID.random()).also {
      when (lifetime) {
        AnchorLifetime.MUTATION -> intermediateAnchorStorage = intermediateAnchorStorage.addRangeMarker(it, rangeStart, rangeEnd, false, false)
        AnchorLifetime.DOCUMENT -> documentIntermediateAnchorStorage = documentIntermediateAnchorStorage.addRangeMarker(it, rangeStart, rangeEnd, false, false)
      }
      rangeMarkerIds.add(it.id)
    }
  }

  override fun removeRangeMarker(markerId: RangeMarkerId) {
    documentIntermediateAnchorStorage = documentIntermediateAnchorStorage.removeRangeMarker(markerId)
    rangeMarkerIds.remove(markerId.id)
  }

  override fun resolveAnchor(anchorId: AnchorId): Long? {
    return intermediateAnchorStorage.resolveAnchor(anchorId)
           ?: documentIntermediateAnchorStorage.resolveAnchor(anchorId)
           ?: original.resolveAnchor(anchorId)
  }

  override fun resolveRangeMarker(markerId: RangeMarkerId): TextRange? {
    return intermediateAnchorStorage.resolveRangeMarker(markerId)
           ?: documentIntermediateAnchorStorage.resolveRangeMarker(markerId)
           ?: original.resolveRangeMarker(markerId)
  }

  override fun batchUpdateAnchors(anchorIds: List<AnchorId>, anchorOffsets: LongArray,
                                  rangeIds: List<RangeMarkerId>, ranges: List<TextRange>) {
    error("transient document $original shouldn't be original of another transient document $this")
  }

  override val text: Text
    get() = intermediateText
  override val timestamp: Long
    get() = TODO("Not yet implemented")
  override val edits: EditLog
    get() = TODO("Not yet implemented")
}

fun MutableEditor.caretsSubsetView(initialCaretIds: Collection<CaretId>): MutableEditor {
  val originalCaret = multiCaret

  return object : MutableEditor by this {
    val simpleCaret = SimpleMutableMultiCaret(document,SimpleMultiCaretState(
      MultiCaretData().addCarets(initialCaretIds.map { originalCaret.multiCaretData.caretsById[it]!! }),
      originalCaret.primaryCaret.takeIf { initialCaretIds.contains(it.caretId) }?.caretId ?: initialCaretIds.first(),
      originalCaret.meta
    ))

    override val multiCaret: MutableMultiCaret = object : MutableMultiCaret by simpleCaret {
      override fun addCarets(positions: List<CaretPosition>): Caret {
        originalCaret.addCarets(positions)
        return simpleCaret.addCarets(positions)
      }

      override fun removeCarets(carets: Collection<Caret>) {
        originalCaret.removeCarets(carets)
        simpleCaret.removeCarets(carets)
      }

      override fun moveCarets(moves: List<Caret>) {
        originalCaret.moveCarets(moves)
        simpleCaret.moveCarets(moves)
      }
    }
  }
}