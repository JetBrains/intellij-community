// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.editor

import andel.intervals.AnchorStorage
import andel.intervals.Interval
import andel.intervals.Intervals
import andel.intervals.IntervalsQuery
import andel.lines.*
import andel.operation.EditLog
import andel.operation.Operation
import andel.operation.Sticky
import andel.text.Text
import andel.text.TextRange
import andel.text.charSequence
import andel.undo.*
import fleet.util.UID
import fleet.util.openmap.BoundedOpenMap
import fleet.util.openmap.MutableBoundedOpenMap
import fleet.util.openmap.MutableOpenMap
import fleet.util.openmap.OpenMap

fun simpleEditor(
  text: Text = Text.fromString(""),
  selectAll: Boolean = false,
  oneLine: Boolean = false,
  writable: Boolean = true,
  softWrapEnabled: Boolean = false,
  guideToSoftWrapBy: Int = -1,
  scrollCommandTimestamp: Long = 0,
  softWrapBuilder: SoftWrapBuilder = FixedWidthSoftWrapBuilder(Float.POSITIVE_INFINITY,
                                                               6f),
  components: BoundedOpenMap<MutableDocument, DocumentComponent> = BoundedOpenMap.emptyBounded(),
): SimpleEditorState {
  val caretPosition = when {
    selectAll -> {
      val end = text.charCount.toLong()
      CaretPosition(end, 0, end)
    }
    else -> CaretPosition(text.charCount.toLong())
  }

  val textForCache = components[SimpleDocumentPasswordKey]?.passwordText ?: text
  val linesCache = layoutLines(text = textForCache,
                               inlays = Intervals.droppingCollapsed().empty(),
                               interlines = Intervals.droppingCollapsed().empty(),
                               softWrapBuilder = softWrapBuilder)
  return SimpleEditorState(
    document = SimpleDocumentState(
      text = text,
      edits = EditLog.empty(),
      anchorStorage = AnchorStorage.empty(),
      undoLog = UndoLogData.EMPTY,
      components = components
    ),
    multiCaret = SimpleMultiCaretState(caretPosition, text, linesCache),
    oneLine = oneLine,
    writable = writable,
    softWrapEnabled = softWrapEnabled,
    guideToSoftWrapBy = guideToSoftWrapBy,
    editorLayout = SimpleEditorLayout(
      linesCacheImpl = linesCache,
      inlays = Intervals.droppingCollapsed().empty(),
      interlines = Intervals.droppingCollapsed().empty(),
      postlines = Intervals.droppingCollapsed().empty(),
      folds = Intervals.droppingCollapsed().empty()
    ),
    focusPlace = EditorFocusPlace.EditorText,
    scrollCommand = if (scrollCommandTimestamp != 0L) EditorScrollCommand(
      startOffset = caretPosition.offset,
      endOffset = caretPosition.offset,
      animate = false,
      xKind = EditorScrollKind.Smallest,
      yKind = EditorScrollKind.Smallest,
      timestamp = scrollCommandTimestamp)
    else null
  )
}

private fun LinesCache.offsetToVCol(text: Text, offset: Long): VCol {
  return offsetOfWidth(
    text = text.view().charSequence(),
    range = TextRange(linesLayout().line(offset).from, offset),
    targetWidth = Float.MAX_VALUE,
    inlays = IntervalsQuery.empty<Any?, Inlay>(),
    foldings = IntervalsQuery.empty<Any?, Fold>()
  ).width
}

data class SimpleEditorLayout(
  val linesCacheImpl: LinesCache,
  override var inlays: Intervals<Long, Inlay>,
  override var interlines: Intervals<Long, Interline>,
  override var postlines: Intervals<Long, Postline>,
  override var folds: Intervals<Long, Fold>,
) : MutableEditorLayout {
  
  override val linesCache: LinesLayout
    get() = linesCacheImpl.linesLayout()
  
  fun edit(before: Text, after: Text, edit: Operation): SimpleEditorLayout {
    val newInlays = inlays.edit(edit)
    val newInterlines = interlines.edit(edit)
    val newPostlines = postlines.edit(edit)
    val newFolds = folds.edit(edit)
    return SimpleEditorLayout(
      linesCacheImpl = linesCacheImpl.edit(
        before = before,
        after = after,
        edit = edit,
        newInlays = newInlays,
        newInterlines = newInterlines,
        newFolds = newFolds,
      ),
      inlays = newInlays,
      interlines = newInterlines,
      postlines = newPostlines,
      folds = newFolds,
    )
  }

  override fun unfold(affectedFolds: List<Interval<*, Fold>>) {}
}

class SimpleEditorLayoutComponent(var state: SimpleEditorLayout) : DocumentComponent {
  override fun edit(before: Text, after: Text, edit: Operation) {
    state = state.edit(before, after, edit)
  }
}

object SimpleEditorLayoutComponentKey : DocumentComponentKey<SimpleEditorLayoutComponent>
object SimpleDocumentPasswordKey : DocumentComponentKey<PasswordComponent>

data class SimpleEditorState(
  override val document: SimpleDocumentState,
  override val multiCaret: SimpleMultiCaretState,
  override val oneLine: Boolean,
  override val writable: Boolean,
  val editorLayout: SimpleEditorLayout,
  val softWrapEnabled: Boolean,
  val guideToSoftWrapBy: Int,
  val scrollCommand: EditorScrollCommand? = null,
  val focusPlace: EditorFocusPlace,
  val userActionTimestamp: Map<EditorCommandType, Long> = emptyMap(),
  override val composableTextRange: TextRange? = null,
) : Editor {
  override val undoLog: UndoLog get() = document.undoLog
  override val layout: EditorLayout
    get() = editorLayout

  val userActionTimestampGetter: () -> Map<EditorCommandType, Long> = { userActionTimestamp }
}

fun SimpleDocumentState.mutate(f: MutableDocument.() -> Unit): SimpleDocumentState {
  val mutableDocument = SimpleMutableDocument(this)
  f(mutableDocument)
  return mutableDocument.state
}

fun SimpleEditorState.mutate(f: MutableEditor.() -> Unit): SimpleEditorState {
  val mutableDocument = SimpleMutableDocument(document)
  val editorLayoutComponent = SimpleEditorLayoutComponent(editorLayout)
  val mutableEditor = SimpleMutableEditor(mutableDocument,
                                          SimpleMutableMultiCaret(mutableDocument, multiCaret),
                                          oneLine,
                                          writable,
                                          editorLayoutComponent,
                                          focusPlace,
                                          scrollCommand,
                                          composableTextRange,
                                          userActionTimestamp)
  mutableDocument.addEditor(mutableEditor)
  f(mutableEditor)
  val documentStateAfter = mutableEditor.document.state
  return this.copy(document = documentStateAfter, multiCaret = mutableEditor.multiCaret.state,
                   editorLayout = mutableEditor.editorLayout.state,
                   scrollCommand = mutableEditor.scrollCommand,
                   focusPlace = mutableEditor.focusPlace,
                   userActionTimestamp = mutableEditor.userActionTimestamp,
                   composableTextRange = mutableEditor.composableTextRange)
}

data class SimpleMultiCaretState(
  override val multiCaretData: MultiCaretData,
  val primaryCaretId: CaretId,
  override val meta: OpenMap<MultiCaretMeta> = MutableOpenMap.empty(),
) : MultiCaret {
  override val carets: List<Caret>
    get() = multiCaretData.carets
  override val primaryCaret: Caret
    get() = multiCaretData.caretsById[primaryCaretId]!!
}

fun SimpleMultiCaretState(caretPosition: CaretPosition, vCol: VCol?): SimpleMultiCaretState {
  val caretId = CaretId(UID.random())
  return SimpleMultiCaretState(
    multiCaretData = MultiCaretData().addCarets(listOf(Caret(
      caretId = caretId,
      position = caretPosition,
      vCol = vCol))),
    primaryCaretId = caretId
  )
}


fun SimpleMultiCaretState(caretPosition: CaretPosition, text: Text, linesCache: LinesCache): SimpleMultiCaretState {
  return SimpleMultiCaretState(caretPosition, linesCache.offsetToVCol(text, caretPosition.offset))
}

class SimpleMutableMultiCaret(private var document: MutableDocument, internal var state: SimpleMultiCaretState) : MutableMultiCaret {
  override val meta: MutableOpenMap<MultiCaretMeta> = state.meta.mutable()
  override val carets: List<Caret>
    get() = state.carets
  override var multiCaretData: MultiCaretData
    get() = state.multiCaretData
    set(value) {
      state = state.copy(multiCaretData = value)
    }
  override var primaryCaret: Caret
    get() = state.primaryCaret
    set(value) {
      state = state.copy(primaryCaretId = value.caretId)
    }

  override fun moveCarets(moves: List<Caret>) {
    if (moves.isEmpty()) return
    val exhaustiveMoves = exhaustiveMoves(moves.map {
      it.copy(position = it.position.coerceInDocument())
    })
    val primaryCaretPosition = moves.find { it.caretId == primaryCaret.caretId }?.position ?: primaryCaret.position
    val mergedCarets = mergeCaretsBeforeMoves(exhaustiveMoves)
    multiCaretData = MultiCaretData().addCarets(mergedCarets)
    primaryCaret = mergedCarets.find { it.position == primaryCaretPosition } ?: mergedCarets.first()
  }

  override fun removeCarets(carets: Collection<Caret>) {
    val oldPrimaryCaretOffset = primaryCaret.offset
    multiCaretData = state.multiCaretData.removeCarets(carets.map { it.caretId })
    if (carets.any { it.caretId == state.primaryCaretId }) {
      val newCarets = multiCaretData.carets
      state = state.copy(
        primaryCaretId = newCarets.findLast { it.offset < oldPrimaryCaretOffset }?.caretId ?: newCarets.firstOrNull()?.caretId
                         ?: CaretId(UID.fromString("undefined")))
    }
  }

  private fun CaretPosition.coerceInDocument(): CaretPosition {
    return copy(
      offset = offset.coerceIn(0, document.text.charCount.toLong()),
      selectionStart = selectionStart.coerceIn(0, document.text.charCount.toLong()),
      selectionEnd = selectionEnd.coerceIn(0, document.text.charCount.toLong()),
    )
  }

  override fun addCarets(positions: List<CaretPosition>): Caret {
    val caretsToAdd = positions.map {
      Caret(position = it.coerceInDocument(), caretId = CaretId(UID.random()))
    }
    val mergedCarets = mergeCaretsBeforeMoves(carets + caretsToAdd)
    multiCaretData = MultiCaretData().addCarets(mergedCarets)
    val primaryCaretPosition = mergedCarets.find { it.caretId == state.primaryCaretId }?.position ?: positions.first()
    primaryCaret = mergedCarets.find { it.position == primaryCaretPosition } ?: mergedCarets.first()
    return mergedCarets.single {
      primaryCaretPosition.offset == it.offset
      || (it.position.selectionStart < primaryCaretPosition.offset
          && primaryCaretPosition.offset < it.position.selectionEnd)
    }
  }
}

class SimpleMutableEditor(
  override val document: SimpleMutableDocument,
  override val multiCaret: SimpleMutableMultiCaret,
  override val oneLine: Boolean,
  override val writable: Boolean,
  val editorLayout: SimpleEditorLayoutComponent,
  var focusPlace: EditorFocusPlace,
  override var scrollCommand: EditorScrollCommand?,
  override var composableTextRange: TextRange?,
  var userActionTimestamp: Map<EditorCommandType, Long>,
) : MutableEditor {
  override val undoLog: UndoLog get() = document.undoLog
  override val components: MutableBoundedOpenMap<MutableEditor, DocumentComponent> = MutableBoundedOpenMap.emptyBounded()
  override val meta: MutableOpenMap<EditorMeta> = MutableOpenMap.empty()
  override fun scrollTo(scrollCommand: EditorScrollCommand) {
    this.scrollCommand = scrollCommand
  }

  override fun switchFocus(place: EditorFocusPlace) {
    focusPlace = place
  }

  override fun command(commandType: EditorCommandType, groupKey: UndoGroupKey, command: UndoScope.() -> Unit) {
    val current = userActionTimestamp.maxOfOrNull { it.value } ?: 0L
    userActionTimestamp += commandType to current.inc()

    val dummyUndoScope = object : UndoScope {
      override fun <T> recordUndoData(operationType: UndoOperationType, data: T) {
        TODO("UndoScope for SimpleEditor is not supported yet")
      }
    }

    if (meta.contains(EditorCommandKey)) {
      dummyUndoScope.command()
      return
    }
    meta[EditorCommandKey] = listOfNotNull(meta[EditorCommandKey], commandType).max()
    document.command(groupKey, multiCaret.carets.map(Caret::position)) {
      dummyUndoScope.command()
      multiCaret.carets.map(Caret::position)
    }
    meta.remove(EditorCommandKey)
  }

  override fun addHistoryPlace() {

  }

  override val layout: MutableEditorLayout
    get() = editorLayout.state
}

data class SimpleDocumentState(
  override val text: Text,
  override val edits: EditLog,
  val anchorStorage: AnchorStorage,
  val undoLog: UndoLogData,
  val components: BoundedOpenMap<MutableDocument, DocumentComponent>,
) : Document {

  override val timestamp: Long get() = edits.timestamp

  override fun resolveAnchor(anchorId: AnchorId): Long? {
    return anchorStorage.resolveAnchor(anchorId)
  }

  override fun resolveRangeMarker(markerId: RangeMarkerId): TextRange? {
    return anchorStorage.resolveRangeMarker(markerId)
  }
}

class SimpleMutableDocument(internal var state: SimpleDocumentState) : MutableDocument {
  private val mutableEditors = mutableListOf<SimpleMutableEditor>()
  private var intermediateAnchorStorage: AnchorStorage = AnchorStorage.empty()

  val editors: List<SimpleMutableEditor> get() = mutableEditors

  fun addEditor(editor: SimpleMutableEditor) {
    mutableEditors.add(editor)
    components[SimpleEditorLayoutComponentKey] = editor.editorLayout
  }

  override val text: Text
    get() = state.text
  override val timestamp: Long
    get() = state.timestamp
  override val edits: EditLog
    get() = state.edits
  var undoLog: UndoLogData
    get() = state.undoLog
    set(undoLog) {
      state = state.copy(undoLog = undoLog)
    }
  override val components: MutableBoundedOpenMap<MutableDocument, DocumentComponent> = state.components.mutable().persistent().mutable()
  override val meta: MutableOpenMap<DocumentMeta> = MutableOpenMap.empty()

  override fun edit(operation: Operation) {
    val textBefore = state.text
    val visibleTextBefore = state.visibleText()
    val textAfter = state.text.mutableView()
      .apply { edit(operation) }
      .text()
    var visibleTextAfter = textAfter
    var visibleOperation = operation
    for (component in components.asMap().values.sortedBy { it.getOrder() }) {
      component.edit(visibleTextBefore, visibleTextAfter, visibleOperation)
      if (component is PasswordComponent) {
        visibleTextAfter = component.passwordText
        visibleOperation = Operation.replaceAt(0, textBefore.view().charSequence().toString(), textAfter.view().charSequence().toString(),
                                               textBefore.charCount.toLong())
      }
    }
    state = state.copy(
      text = textAfter,
      edits = state.edits.append(UID.random(), operation).trim(),
      anchorStorage = state.anchorStorage.edit(textBefore, textAfter, operation)
    )
    intermediateAnchorStorage = intermediateAnchorStorage.edit(textBefore, textAfter, operation)
    for (editor in editors) {
      val updatedCarets = editor.multiCaret.state.multiCaretData.transformOnto(operation, Sticky.LEFT)
      editor.multiCaret.state = editor.multiCaret.state.copy(
        multiCaretData = updatedCarets.mergeCoincidingCarets(),
        primaryCaretId = updatedCarets.mergedAnchors[editor.multiCaret.state.primaryCaretId] ?: editor.multiCaret.state.primaryCaretId
      )
    }
  }

  fun command(
    groupKey: UndoGroupKey,
    caretsBefore: List<CaretPosition>,
    command: () -> List<CaretPosition>,
  ) {
    val idBefore = state.edits.timestamp
    val undoLogBefore = state.undoLog
    val caretsAfter = command.invoke()
    val idAfter = state.edits.timestamp
    val undoGroup = DocumentUndoGroup(
      entryIndices = (idBefore until idAfter).toList(),
      entryIndexFrom = idBefore,
      caretStateBefore = caretsBefore,
      caretStateAfter = caretsAfter,
      attributes = groupKey.toAttributes()
    )
    state = state.copy(undoLog = undoLogBefore.addGroup(undoGroup, state))
  }

  override fun createAnchor(offset: Long, lifetime: AnchorLifetime, sticky: Sticky): AnchorId {
    val anchorId = AnchorId(UID.random())
    when (lifetime) {
      AnchorLifetime.MUTATION -> intermediateAnchorStorage = intermediateAnchorStorage.addAnchor(anchorId, offset, sticky)
      AnchorLifetime.DOCUMENT -> state = state.copy(anchorStorage = state.anchorStorage.addAnchor(anchorId, offset, sticky))
    }
    return anchorId
  }

  override fun removeAnchor(anchorId: AnchorId) {
    intermediateAnchorStorage = intermediateAnchorStorage.removeAnchor(anchorId)
    state = state.copy(anchorStorage = state.anchorStorage.removeAnchor(anchorId))
  }

  override fun resolveAnchor(anchorId: AnchorId): Long? {
    return intermediateAnchorStorage.resolveAnchor(anchorId) ?: state.resolveAnchor(anchorId)
  }

  override fun createRangeMarker(rangeStart: Long, rangeEnd: Long, lifetime: AnchorLifetime): RangeMarkerId {
    val markerId = RangeMarkerId(UID.random())
    when (lifetime) {
      AnchorLifetime.MUTATION -> intermediateAnchorStorage = intermediateAnchorStorage.addRangeMarker(markerId, rangeStart, rangeEnd, false,
                                                                                                      false)
      AnchorLifetime.DOCUMENT -> state = state.copy(
        anchorStorage = state.anchorStorage.addRangeMarker(markerId, rangeStart, rangeEnd, false, false))
    }
    return markerId
  }

  override fun removeRangeMarker(markerId: RangeMarkerId) {
    intermediateAnchorStorage = intermediateAnchorStorage.removeRangeMarker(markerId)
    state = state.copy(anchorStorage = state.anchorStorage.removeRangeMarker(markerId))
  }

  override fun resolveRangeMarker(markerId: RangeMarkerId): TextRange? {
    return intermediateAnchorStorage.resolveRangeMarker(markerId) ?: state.resolveRangeMarker(markerId)
  }

  override fun batchUpdateAnchors(
    anchorIds: List<AnchorId>, anchorOffsets: LongArray,
    rangeIds: List<RangeMarkerId>, ranges: List<TextRange>,
  ) {
    state = state.copy(anchorStorage = state.anchorStorage.batchUpdate(anchorIds, anchorOffsets, rangeIds, ranges))
  }
}

sealed class EditorScrollKind {
  data object None : EditorScrollKind()
  data class Ratio(val ratio: Float = 0.381f) : EditorScrollKind()
  data object GoldenRatio : EditorScrollKind()
  data object Center : EditorScrollKind()
  data object Smallest : EditorScrollKind()
  data object Exact : EditorScrollKind()
  data object Gravitate : EditorScrollKind()
  data class Padded(val padBeforeChars: Int, val padAfterChars: Int) : EditorScrollKind()
}


/**
 * Scroll commands are asynchronous by nature. In order to prevent an unintended scroll,
 * we need to add a condition that checks validity of the scroll command.
 *
 * Each scroll command could specify [author] and [expectedAuthor]. If [author] is null then "Any()" will be used instead.
 * A scroll command would be dropped if [author] of the scroll state is not equal to [expectedAuthor].
 * If [expectedAuthor] is null then a scroll command will be executed unconditionally.
 * Each user event updates [author] to "Any()". Clamp doesn't update [author].
 *
 * @see noria.ui.components.editor.EditorViewportObserverKey
 */
data class EditorScrollCommand(
  val startOffset: Long,
  val endOffset: Long,
  val xKind: EditorScrollKind,
  val yKind: EditorScrollKind,
  val animate: Boolean,
  val timestamp: Long,
  val author: Any? = null,
  val expectedAuthor: Any? = null,
)

class PasswordComponent(var passwordText: Text) : DocumentComponent {
  companion object {
    fun hideText(text: Text): Text = Text.fromString("\u2022".repeat(text.charCount.toLong().toInt()))
  }

  override fun edit(before: Text, after: Text, edit: Operation) {
    passwordText = hideText(after)
  }

  override fun getOrder(): Int = -1
}

fun SimpleDocumentState.visibleText(): Text = components[SimpleDocumentPasswordKey]?.passwordText ?: text
