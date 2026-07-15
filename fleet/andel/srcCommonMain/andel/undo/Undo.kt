// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.undo

import andel.editor.Caret
import andel.editor.CaretPosition
import andel.editor.Document
import andel.editor.MutableDocument
import andel.editor.MutableEditor
import andel.editor.carets
import andel.editor.setCarets
import andel.operation.EditLog
import andel.operation.Operation
import andel.operation.Sticky
import andel.operation.compose
import andel.operation.invert
import andel.operation.isIdentity
import andel.operation.normalizeHard
import andel.operation.transform
import andel.operation.transformOnto
import fleet.util.chunkedBy
import kotlinx.serialization.Serializable


@Serializable
data class UndoOperationType(val id: String, val version: Int)

/**
 * Basic interface to provide custom undo logic for arbitrary state.
 */
interface UndoOperation<T> {
  fun undo(data: T)
  fun redo(data: T)
}

interface UndoScope {
  fun <T> recordUndoData(operationType: UndoOperationType, data: T)
}

// sorted from oldest to newest
fun AbstractUndoGroup.revertedGroups(isRedo: Boolean): List<UndoGroupReference>? =
  if (isRedo)
    attributes.map[UndoGroupAttributes.redo]
  else
    attributes.map[UndoGroupAttributes.undo]

/**
 * Builds the logical undo or redo stack from the raw append-only undo log.
 *
 * The underlying log records every command, including commands produced by undo and redo themselves.
 * This means the latest log entry is not necessarily the next entry that should be undone.
 *
 * To recover the user-visible stack, this function walks the log backwards and interprets group attributes:
 * normal groups are yielded as available stack entries, while groups marked with
 * [UndoGroupAttributes.undo] or [UndoGroupAttributes.redo] are treated as inversions of earlier groups.
 * Those inversion groups are not yielded for the same direction. Instead, traversal jumps to the group
 * preceding the first reverted group, effectively removing the reverted range from the projected stack.
 *
 * Example for undo:
 * raw log = edit1, edit2, edit3, undo(edit3)
 * projected undo stack = edit2, edit1
 * projected redo stack = undo(edit3)
 *
 * When [isRedo] is true, traversal stops as soon as it reaches a suffix that contains no undo-produced
 * groups, because redo is only available for operations that were previously undone.
 */
fun UndoLog.asUndoStack(isRedo: Boolean): Sequence<CustomIndexedValue<UndoGroupReference, AbstractUndoGroup>> =
  sequence {
    var idx = last()
    while (idx != null) {
      val revertedGroups = idx.value.revertedGroups(isRedo)
      idx = if (revertedGroups == null) {
        if (isRedo && idx.value.attributes.map[UndoGroupAttributes.undo] == null) break
        yield(idx)
        previous(idx.index)
      }
      else {
        previous(revertedGroups.first())
      }
    }
  }

/**
 * It's a "spread invert" — selectively undo only certain edits from a range,
 * correctly rebasing around the ones being skipped. This is the core of
 * non-contiguous undo (e.g. undoing edit #3 when edits #4 and #5 happened after it).
 */
fun EditLog.composeSpreadInverted(
  entryIndexFrom: Long,
  entryIndexTo: Long,
  entryIndices: List<Long>,
): Operation {
  val editLog = this
  var undoOperation = Operation.empty()
  val indexedFragment = editLog.slice(entryIndexFrom, entryIndexTo) zip (entryIndexFrom.toInt() until entryIndexTo.toInt())
  indexedFragment
    .chunkedBy { it.second.toLong() in entryIndices }
    .chunked(2)
    .forEach { pair ->
      val includePart = pair.first()
      undoOperation = includePart.map { it.first }.compose().invert().compose(undoOperation)
      val excludePart = pair.getOrNull(1)
      if (excludePart != null) {
        undoOperation = undoOperation.transform(excludePart.map { it.first }.compose(), Sticky.LEFT)
      }
    }
  return undoOperation
}


data class EditGroup(
  val attributes: UndoGroupAttributes,
  val entryIndexFrom: Long,
  val entryIndices: List<Long>,
  val caretStateBefore: List<CaretPosition>?,
  val caretStateAfter: List<CaretPosition>?,
  val openForMerge: Boolean,
) {
  fun merge(other: EditGroup): EditGroup {
    return EditGroup(
      openForMerge = other.openForMerge,
      entryIndices = entryIndices + other.entryIndices,
      entryIndexFrom = entryIndexFrom,
      caretStateBefore = caretStateBefore,
      caretStateAfter = other.caretStateAfter,
      attributes = mergeAttributes(this.attributes, other.attributes)
    )
  }

  val entryIndexTo: Long
    get() = if (entryIndices.isEmpty()) {
      entryIndexFrom
    }
    else {
      entryIndices.last() + 1
    }

  private fun mergeAttributes(one: UndoGroupAttributes, another: UndoGroupAttributes): UndoGroupAttributes {
    return UndoGroupAttributes(one.map.merge(another.map))
  }
}

/**
 * Decides how to record [newGroup] — a freshly completed edit operation — onto the undo stack,
 * given [lastGroup], the most recent entry already on the stack for the same origin.
 */
fun amendDocumentEdit(
  document: Document,
  lastGroup: EditGroup?,
  newGroup: EditGroup,
): UndoStack.Action<EditGroup> {
  val editList = newGroup.entryIndices.map { document.edits[it] }
  val includeIntoLog = newGroup.attributes.map[UndoGroupAttributes.includeIntoLog] ?: !editList.all { it.isIdentity() }
  return if (includeIntoLog) {
    if (lastGroup != null && lastGroup.openForMerge && canMergeEditGroups(lastGroup, newGroup)) {
      UndoStack.Action.Replace(lastGroup.merge(newGroup))
    }
    else {
      UndoStack.Action.Append(newGroup)
    }
  }
  else if (lastGroup != null && newGroup.caretStateBefore != newGroup.caretStateAfter) {
    UndoStack.Action.Replace(lastGroup.copy(openForMerge = false))
  }
  else {
    UndoStack.Action.Nothing()
  }
}

private fun canMergeEditGroups(lastGroup: EditGroup, newGroup: EditGroup): Boolean {
  val lastKey = lastGroup.attributes.map[UndoGroupAttributes.mergeKey]
  val newKey = newGroup.attributes.map[UndoGroupAttributes.mergeKey]
  return (newKey != null && lastKey == newKey) || newGroup.attributes.map[UndoGroupAttributes.mergeAlways] == true
}

/**
 * Applies one undo or redo step to [editor], inverting [editGroup] — the original captured operation.
 *
 * This function is an implementation detail of the undo machinery and should not be used directly.
 *
 * **Concurrent-edit rebasing.** Edits recorded after the group was originally applied are composed, and
 * the inverted edit is OT-transformed over the composition so that it applies correctly to the current document state,
 * and the saved caret positions are similarly rebased so they map to current offsets.
 *
 * **Two-phase caret handling** (controlled by [isCaretMovementUndoStep]):
 * - If the carets have moved since the operation was recorded *and* [isCaretMovementUndoStep] is `true`,
 *   only the carets are repositioned (to [EditGroup.caretStateAfter]) and `null` is returned — the text
 *   is left untouched. The caller should keep [editGroup] on the same stack so the next invocation proceeds to
 *   the text edit.
 * - Otherwise the inverted edit is applied to the document and carets are set to [EditGroup.caretStateBefore].
 *   A new [EditGroup] capturing the reverse operation and current caret positions is returned; the caller
 *   should push it onto the opposite stack.
 */
fun editorUndo(
  editor: MutableEditor,
  compositionCache: CompositionCache,
  editGroup: EditGroup,
  isCaretMovementUndoStep: Boolean,
): EditGroup? {
  val inversion = computeInversion(compositionCache, editGroup)
  val operation = inversion.operation
  val caretStateBefore = inversion.caretStateBefore
  val caretStateAfter = inversion.caretStateAfter

  return when {
    isCaretMovementUndoStep && !caretStateAfter.isNullOrEmpty() && caretStateAfter.toSet() != editor.carets.map { it.position }.toSet() -> {
      editor.setCarets(caretStateAfter)
      null
    }
    else -> {
      val entryIndexFrom = editor.document.edits.timestamp
      val caretStateBeforeUndo = editor.carets.map(Caret::position)
      editor.document.edit(operation)
      if (!caretStateBefore.isNullOrEmpty()) {
        editor.setCarets(caretStateBefore)
      }
      EditGroup(
        attributes = editGroup.attributes,
        entryIndexFrom = entryIndexFrom,
        entryIndices = (entryIndexFrom until editor.document.edits.timestamp).toList(),
        caretStateBefore = caretStateBeforeUndo,
        caretStateAfter = editor.carets.map(Caret::position),
        openForMerge = false,
      )
    }
  }
}

/**
 * Applies one undo or redo step to [document], inverting [editGroup] — the original captured operation.
 *
 * Same as [editorUndo], but operates on a bare document without an editor.
 *
 * This function is an implementation detail of the undo machinery and should not be used directly.
 *
 * @return the [EditGroup] capturing the reverse operation, to push onto the opposite stack.
 */
fun documentUndo(
  document: MutableDocument,
  compositionCache: CompositionCache,
  editGroup: EditGroup,
): EditGroup {
  val inversion = computeInversion(compositionCache, editGroup)
  val entryIndexFrom = document.edits.timestamp
  document.edit(inversion.operation)
  return EditGroup(
    attributes = editGroup.attributes,
    entryIndexFrom = entryIndexFrom,
    entryIndices = (entryIndexFrom until document.edits.timestamp).toList(),
    caretStateBefore = inversion.caretStateAfter,
    caretStateAfter = inversion.caretStateBefore,
    openForMerge = false,
  )
}

private class Inversion(
  val operation: Operation,
  /** [EditGroup.caretStateBefore] rebased to the document state after applying [operation]. */
  val caretStateBefore: List<CaretPosition>?,
  /** [EditGroup.caretStateAfter] rebased to the current document state. */
  val caretStateAfter: List<CaretPosition>?,
)

private fun computeInversion(compositionCache: CompositionCache, editGroup: EditGroup): Inversion {
  val baseAfter = compositionCache.computeComposition(editGroup.entryIndexTo, compositionCache.edits.timestamp)
  val baseInside = compositionCache.edits.slice(editGroup.entryIndexFrom, editGroup.entryIndexTo).compose()
  val operation = compositionCache.edits.composeSpreadInverted(editGroup.entryIndexFrom, editGroup.entryIndexTo, editGroup.entryIndices)
    .transform(baseAfter, Sticky.RIGHT)
  return Inversion(
    operation = operation,
    caretStateBefore = editGroup.caretStateBefore?.rebase(baseInside.compose(baseAfter).compose(operation).normalizeHard()),
    caretStateAfter = editGroup.caretStateAfter?.rebase(baseAfter),
  )
}

private fun List<CaretPosition>.rebase(base: Operation): List<CaretPosition> =
  map { pos -> pos.transformOnto(base, Sticky.RIGHT) }
