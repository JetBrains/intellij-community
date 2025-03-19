// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.editor

import andel.operation.EditLog
import andel.intervals.Interval
import andel.intervals.Intervals
import andel.lines.*
import andel.operation.Operation
import andel.operation.Sticky
import andel.text.Text
import andel.text.TextRange
import andel.undo.*
import andel.undo.NavigationUndoGroupKey
import fleet.util.UID
import fleet.util.openmap.BoundedOpenMap
import fleet.util.openmap.Key
import fleet.util.openmap.MutableOpenMap
import fleet.util.openmap.OpenMap
import fleet.util.serialization.DataSerializer
import kotlinx.serialization.Serializable

interface DocumentComponent {
  fun transformCaret(caret: Caret): Caret? {
    return caret
  }

  /**
   * update document-related stuff such as the offsets stored inside the component
   */
  fun edit(before: Text, after: Text, edit: Operation) {}

  /**
   *
   */
  fun onCommit() {}

  /**
   * (A hacky way to order the components' update until zajac makes it as he wants)
   *
   * Some components might depend on others and thus might want to expect some state on `edit`
   * calls, i.e. during my component's `edit` whether other component I look upon was "edited" or not.
   * Such relations should be deterministic hence we introduce a way to order them.
   */
  fun getOrder(): Int {
    return 0
  }
}

interface DocumentComponentKey<C : DocumentComponent> : Key<C, MutableDocument>
interface EditorComponentKey<C : Any> : Key<C, MutableEditor>

@Serializable(with = CaretId.Serializer::class)
data class CaretId(val id: UID) {
  class Serializer : DataSerializer<CaretId, UID>(UID.serializer()) {
    override fun fromData(data: UID): CaretId = CaretId(data)
    override fun toData(value: CaretId): UID = value.id
  }
}

enum class AnchorLifetime {
  MUTATION,
  DOCUMENT
}

@Serializable(with = AnchorId.Serializer::class)
data class AnchorId(val id: UID) {
  class Serializer : DataSerializer<AnchorId, UID>(UID.serializer()) {
    override fun fromData(data: UID): AnchorId = AnchorId(data)
    override fun toData(value: AnchorId): UID = value.id
  }
}

@Serializable(with = RangeMarkerId.Serializer::class)
data class RangeMarkerId(val id: UID) {
  class Serializer : DataSerializer<RangeMarkerId, UID>(UID.serializer()) {
    override fun fromData(data: UID): RangeMarkerId = RangeMarkerId(data)
    override fun toData(value: RangeMarkerId): UID = value.id
  }
}

interface MultiCaretMeta
interface EditorMeta
interface DocumentMeta

interface CaretMetaKey<V : Any> : Key<V, MultiCaretMeta>
interface EditorMetaKey<V : Any> : Key<V, EditorMeta>
interface DocumentMetaKey<V : Any> : Key<V, DocumentMeta>

interface MultiCaret {
  val meta: OpenMap<MultiCaretMeta>
  val carets: List<Caret>
  val multiCaretData: MultiCaretData
  val primaryCaret: Caret
}

interface MutableMultiCaret : MultiCaret {
  override var primaryCaret: Caret

  /**
   * Adds caret at denoted positions to this multicaret.
   *
   * @return the new primary caret
   */
  fun addCarets(positions: List<CaretPosition>): Caret
  fun removeCarets(carets: Collection<Caret>)
  fun moveCarets(moves: List<Caret>)
}

interface Editor {
  val document: Document
  val undoLog: UndoLog
  val multiCaret: MultiCaret
  val oneLine: Boolean
  val writable: Boolean

  val layout: EditorLayout

  val composableTextRange: TextRange?
}

interface MutableEditor : Editor {
  override val document: MutableDocument
  val components: BoundedOpenMap<MutableEditor, DocumentComponent>
  val meta: MutableOpenMap<EditorMeta>
  val scrollCommand: EditorScrollCommand?
  override val multiCaret: MutableMultiCaret
  override val layout: MutableEditorLayout

  fun scrollTo(scrollCommand: EditorScrollCommand)

  fun command(commandType: EditorCommandType,
              groupKey: UndoGroupKey = commandType.defaultGroupKey(),
              command: UndoScope.() -> Unit)

  fun addHistoryPlace()

  fun switchFocus(place: EditorFocusPlace)

  override var composableTextRange: TextRange?
}

interface EditorLayout {
  val linesCache: LinesLayout
  val inlays: Intervals<*, Inlay>
  val interlines: Intervals<*, Interline>
  val postlines: Intervals<*, Postline>
  val folds: Intervals<*, Fold>
}

interface MutableEditorLayout : EditorLayout {
  fun unfold(affectedFolds: List<Interval<*, Fold>>)
}

// Order is significant: the greater index, the more specific is the command type
@Serializable
enum class EditorCommandType {
  NAVIGATION, EDIT,
  GENERATED_EDIT,

  // This is not really a good third action command, but rather an ad-hoc solution not to introduce an actionTracker.
  COMMENT
}

fun EditorCommandType.defaultGroupKey() = when (this) {
  EditorCommandType.NAVIGATION -> NavigationUndoGroupKey
  EditorCommandType.EDIT -> DefaultUndoGroupKey
  EditorCommandType.GENERATED_EDIT -> DefaultUndoGroupKey
  EditorCommandType.COMMENT -> DefaultUndoGroupKey
}

interface Document {
  val text: Text
  val timestamp: Long
  val edits: EditLog
  fun resolveAnchor(anchorId: AnchorId): Long?
  fun resolveRangeMarker(markerId: RangeMarkerId): TextRange?
}

interface MutableDocument : Document {
  val components: BoundedOpenMap<MutableDocument, DocumentComponent>
  val meta: MutableOpenMap<DocumentMeta>
  fun edit(operation: Operation)

  fun createAnchor(offset: Long, lifetime: AnchorLifetime, sticky: Sticky = Sticky.LEFT): AnchorId
  fun removeAnchor(anchorId: AnchorId)

  fun createRangeMarker(rangeStart: Long, rangeEnd: Long, lifetime: AnchorLifetime): RangeMarkerId
  fun removeRangeMarker(markerId: RangeMarkerId)

  fun batchUpdateAnchors(anchorIds: List<AnchorId>, anchorOffsets: LongArray,
                         rangeIds: List<RangeMarkerId>, ranges: List<TextRange>)
}

object EditorCommandKey : EditorMetaKey<EditorCommandType>
