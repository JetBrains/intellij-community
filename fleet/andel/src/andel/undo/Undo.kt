// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.undo

import andel.editor.CaretPosition
import andel.editor.Document
import andel.operation.*
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

// todo trim log when it's too long

data class UndoInfo(
  val edit: Operation,
  val isRedo: Boolean,
  val groupsToUndo: List<UndoGroupReference>,
  val groupsToUndoAttributes: List<UndoGroupAttributes>,
  val caretStateBefore: List<CaretPosition>?,
  val caretStateAfter: List<CaretPosition>?,
  // false - only if undo touches one document
  // true - if this undo toches several document (global undo)
  val global: Boolean,
  val description: String?
) {

  val groupKey: UndoGroupKey
    get() =
    if (isRedo) RedoUndoGroupKey(groupsToUndo, description)
    else UndoUndoGroupKey(groupsToUndo, description)
}

// sorted from oldest to newest
fun AbstractUndoGroup.revertedGroups(isRedo: Boolean) =
  if (isRedo)
    attributes.map[UndoGroupAttributes.redo]
  else
    attributes.map[UndoGroupAttributes.undo]

fun UndoLog.asUndoStack(isRedo: Boolean): Sequence<CustomIndexedValue<UndoGroupReference, AbstractUndoGroup>> =
  sequence {
    var idx = last()
    while (idx != null) {
      val lastGroupShift = idx.value.revertedGroups(isRedo)
      idx = if (lastGroupShift == null) {
        if (isRedo && idx.value.attributes.map[UndoGroupAttributes.undo] == null) break
        yield(idx)
        previous(idx.index)
      }
      else {
        previous(lastGroupShift.first())
      }
    }
  }

fun undoInfo(document: Document, undoLog: UndoLog): UndoInfo? {
  val lastGroup = undoLog.asUndoStack(false).firstOrNull() ?: return null
  val extraGroupsInBlock = (lastGroup.value.attributes.map[UndoGroupAttributes.blockClose]?.map {
    CustomIndexedValue(it, undoLog.resolve(it))
  } ?: emptyList())
  val groupsToUndoRange = extraGroupsInBlock + listOf(lastGroup)
  if (groupsToUndoRange.any { it.value is DocumentUndoGroup && !it.value.isPresentInEditLog(document.edits) }) {
    return null
  }

  return undoRedoInfo(document = document,
                      isRedo = false,
                      groupsToUndoRange = groupsToUndoRange,
                      undoLog = undoLog)
}

fun redoInfo(document: Document, undoLog: UndoLog): UndoInfo? {
  val lastGroup = undoLog.asUndoStack(true).firstOrNull() ?: return null
  if (lastGroup.value is DocumentUndoGroup && !lastGroup.value.isPresentInEditLog(document.edits)) {
    return null
  }
  return undoRedoInfo(document = document,
                      isRedo = true,
                      groupsToUndoRange = listOf(lastGroup),
                      undoLog = undoLog)
}

fun EditLog.composeSpreadInverted(entryIndexFrom: Long,
                                  entryIndexTo: Long, entryIndices: List<Long>): Operation {
  val editLog = this
  var undoOperation = Operation.empty()
  val indexedFragment = editLog.slice(entryIndexFrom, entryIndexTo) zip (entryIndexFrom.toInt() until entryIndexTo.toInt())
  indexedFragment.chunkedBy { it.second.toLong() in entryIndices }.chunked(2).map { pair ->
    val includePart = pair.first()
    undoOperation = includePart.map { it.first }.compose().invert().compose(undoOperation)
    val excludePart = pair.getOrNull(1)
    if (excludePart != null) {
      undoOperation = undoOperation.transform(excludePart.map { it.first }.compose(), Sticky.LEFT)
    }
  }
  return undoOperation
}

private fun undoRedoInfo(document: Document,
                         isRedo: Boolean,
                         groupsToUndoRange: List<CustomIndexedValue<UndoGroupReference, AbstractUndoGroup>>,
                         undoLog: UndoLog): UndoInfo {

  val groupsToUndoReferences = groupsToUndoRange.map { it.index }
  val groupsToUndoAttributes = groupsToUndoRange.map { it.value.attributes }
  val description = groupsToUndoRange.last().value.attributes.map[UndoGroupAttributes.description]

  if (groupsToUndoRange.any { it.value !is DocumentUndoGroup || it.value.globalGroups.isNotEmpty() }) {
    return UndoInfo(
      edit = Operation.empty(),
      isRedo = isRedo,
      groupsToUndo = groupsToUndoReferences,
      groupsToUndoAttributes = groupsToUndoAttributes,
      caretStateBefore = null,
      caretStateAfter = null,
      global = true,
      description = description
    )
  }

  val groupsToUndo = groupsToUndoRange.map { it.value as DocumentUndoGroup }
  val globalGroups = groupsToUndo.flatMap { it.globalGroups }
  val entryIndexFrom = groupsToUndo.first().entryIndexFrom
  val entryIndexTo = groupsToUndo.last().entryIndexTo
  val entryIndices = groupsToUndo.flatMap(DocumentUndoGroup::entryIndices)
  val caretStateBefore = groupsToUndo.first().caretStateBefore
  val caretStateAfter = groupsToUndo.last().caretStateAfter

  val entries: EditLog = document.edits
  val baseAfter: Operation = undoLog.computeComposition(document.edits, entryIndexTo, entries.timestamp)
  val baseInside: Operation = entries.slice(entryIndexFrom, entryIndexTo).compose()
  val undoOperation = entries.composeSpreadInverted(entryIndexFrom, entryIndexTo, entryIndices).transform(baseAfter, Sticky.RIGHT)
  return UndoInfo(
    edit = undoOperation,
    isRedo = isRedo,
    groupsToUndo = groupsToUndoReferences,
    groupsToUndoAttributes = groupsToUndoAttributes,
    caretStateBefore = caretStateBefore?.rebase(baseInside.compose(baseAfter).compose(undoOperation).normalizeHard()),
    caretStateAfter = caretStateAfter?.rebase(baseAfter),
    global = globalGroups.isNotEmpty(),
    description = description
  )
}

private fun List<CaretPosition>.rebase(base: Operation): List<CaretPosition> = map { pos ->
  pos.transformOnto(base, Sticky.RIGHT)
}

fun UndoLogData.addGroupChange(newGroup: DocumentUndoGroup, document: Document): UndoLogData.UndoLogChange {
  val editList = newGroup.entryIndices.map { document.edits[it] }
  val includeIntoLog = newGroup.globalGroups.isNotEmpty() || newGroup.attributes.map[UndoGroupAttributes.includeIntoLog] ?: !editList.all { it.isIdentity() }
  if (!includeIntoLog) {
    return if (newGroup.caretStateBefore == newGroup.caretStateAfter)
      UndoLogData.UndoLogChange(null, -1, this.openForMerge)
    else
      UndoLogData.UndoLogChange(null, -1, false)
  }
  val lastGroup = last()?.value
  return if (lastGroup != null && newGroup.globalGroups == lastGroup.globalGroups && openForMerge && canMerge(lastGroup, newGroup)) {
    UndoLogData.UndoLogChange(
      lastGroup.merge(newGroup),
      size - 1,
      true
    )
  }
  else {
    UndoLogData.UndoLogChange(
      newGroup,
      size,
      true
    )
  }
}

private fun canMerge(lastGroup: DocumentUndoGroup, newGroup: DocumentUndoGroup): Boolean {
  val lastKey = lastGroup.attributes.map[UndoGroupAttributes.mergeKey]
  val newKey = newGroup.attributes.map[UndoGroupAttributes.mergeKey]
  return (newKey != null && lastKey == newKey) || newGroup.attributes.map[UndoGroupAttributes.mergeAlways] == true
}

internal fun UndoLogData.addGroup(newGroup: DocumentUndoGroup, document: Document): UndoLogData {
  return addGroupChange(newGroup, document).apply(this)
}
