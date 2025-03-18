// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:UseSerializers(BifurcanListSerializer::class)

package andel.undo

import andel.operation.EditLog
import andel.editor.CaretPosition
import andel.operation.Operation
import andel.operation.compose
import andel.operation.normalizeHard
import fleet.util.*
import fleet.util.openmap.SerializableKey
import fleet.util.openmap.SerializableOpenMap
import fleet.util.openmap.SerializedValue
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.jvm.JvmInline

data class CompositionCache(
  val operation: Operation,
  val fromEdit: Long,
  val toEdit: Long,
)

@Serializable
data class UndoGroupAttributes(val map: SerializableOpenMap<UndoGroupAttributes>) {
  fun <V : Any> with(key: SerializableKey<V, UndoGroupAttributes>, value: V): UndoGroupAttributes {
    return UndoGroupAttributes(map.assoc(key, value))
  }

  fun <V : Any> withNotNull(key: SerializableKey<V, UndoGroupAttributes>, value: V?): UndoGroupAttributes {
    return if (value == null) this else with(key, value)
  }

  fun join(other: UndoGroupAttributes?): UndoGroupAttributes =
    when {
      other == null -> this
      else -> UndoGroupAttributes(map.merge(other.map))
    }

  companion object {
    fun <V : Any> with(key: SerializableKey<V, UndoGroupAttributes>, value: V): UndoGroupAttributes {
      return empty().with(key, value)
    }

    fun empty() = UndoGroupAttributes(SerializableOpenMap.empty())

    val description = SerializableKey<String, UndoGroupAttributes>("undoGroup.description", String.serializer())
    val mergeKey = SerializableKey<String, UndoGroupAttributes>("undoGroup.merge.key", String.serializer())
    val mergeAlways = SerializableKey<Boolean, UndoGroupAttributes>("undoGroup.merge.always", Boolean.serializer())
    val blockOpen = SerializableKey<Unit, UndoGroupAttributes>("undoGroup.block.open", Unit.serializer())
    val blockClose = SerializableKey<List<UndoGroupReference>, UndoGroupAttributes>("undoGroup.block.close", ListSerializer(UndoGroupReference.serializer()))
    val undo = SerializableKey<List<UndoGroupReference>, UndoGroupAttributes>("undoGroup.undo", ListSerializer(UndoGroupReference.serializer()))
    val redo = SerializableKey<List<UndoGroupReference>, UndoGroupAttributes>("undoGroup.redo", ListSerializer(UndoGroupReference.serializer()))

    // default: include if contains some changes
    val includeIntoLog = SerializableKey<Boolean, UndoGroupAttributes>("undoGroup.includeIntoLog", Boolean.serializer())
    val renameSession = SerializableKey<UID, UndoGroupAttributes>("undoGroup.rename.session", UID.serializer())
    val refactoringSession = SerializableKey<UID, UndoGroupAttributes>("undoGroup.refactoring.session", UID.serializer())
    val expensiveIndentationSession = SerializableKey<UID, UndoGroupAttributes>("undoGroup.indent.expensive.session", UID.serializer())
    val globalGroups = SerializableKey<List<GlobalUndoLogRef>, UndoGroupAttributes>("undoGroup.globalGroups", ListSerializer(Long.serializer()))
  }
}

interface AbstractUndoGroup {
  val attributes: UndoGroupAttributes
  val contentDebugInfo: String
}

@Serializable
data class DocumentUndoGroup(
  override val attributes: @Contextual UndoGroupAttributes,
  val entryIndexFrom: Long,
  val entryIndices: List<Long>,
  val caretStateBefore: List<CaretPosition>?,
  val caretStateAfter: List<CaretPosition>?,
) : AbstractUndoGroup {
  val globalGroups: List<GlobalUndoLogRef> = attributes.map[UndoGroupAttributes.globalGroups] ?: emptyList()

  override val contentDebugInfo: String
    get() = entryIndices.toString()

  val entryIndexTo: Long
    get() = if (entryIndices.isEmpty()) {
      entryIndexFrom
    }
    else {
      entryIndices.last() + 1
    }

  fun isPresentInEditLog(editLog: EditLog): Boolean {
    return entryIndexFrom >= editLog.timestamp - editLog.opCount
  }

  private fun UndoGroupAttributes.mergeWith(other: UndoGroupAttributes): UndoGroupAttributes {
    return UndoGroupAttributes(map.merge(other.map))
  }

  fun merge(other: DocumentUndoGroup): DocumentUndoGroup {
    require(this.globalGroups.isEmpty() && other.globalGroups.isEmpty()) // TODO extendable
    return DocumentUndoGroup(
      entryIndices = entryIndices + other.entryIndices,
      entryIndexFrom = entryIndexFrom,
      caretStateBefore = caretStateBefore,
      caretStateAfter = other.caretStateAfter,
      attributes = this.attributes.mergeWith(other.attributes)
    )
  }
}

typealias IndexedUndoGroup = CustomIndexedValue<UndoGroupReference, AbstractUndoGroup>

/**
 * Append-only sequence of undo groups representing subsequent undoable changes of a document or file.
 * Undo groups form a kind of bracket sequence in respect of undo and at the same form a bracket sequence in respect of redo.
 * When the document is opened for a file, undo log for represents a concatenation of both lists stored for file and for document.
 *
 * Undo groups are identified by {UndoGroupReference}, a pair of list id and a position in that list.
 * Those lists do not correspond one-to-one with undo logs.
 */
interface UndoLog {
  fun last(): IndexedUndoGroup?

  /**
   * previous group of a group in a log
   */
  fun previous(reference: UndoGroupReference): IndexedUndoGroup?
  fun next(reference: UndoGroupReference): IndexedUndoGroup?

  /**
   * compare two indices in the log, oldest is the least
   */
  val indicesComparator: Comparator<UndoGroupReference>
  fun asReversedSequence(): Sequence<AbstractUndoGroup> {
    return asReversedSequenceIndexed().map { it.value }
  }

  fun asReversedSequenceIndexed(): Sequence<IndexedUndoGroup> {
    return generateSequence(last()) {
      previous(it.index)
    }
  }

  fun resolve(reference: UndoGroupReference): AbstractUndoGroup
  fun computeComposition(edits: EditLog, fromTimestamp: Long, toTimestamp: Long): Operation
}

class UndoLogData(
  val id: UndoLogDataReference,
  val undoStack: IBifurcanVector<DocumentUndoGroup>, // Do not use
  val openForMerge: Boolean,
  var undoBaseOperationCache: CompositionCache? = null,
) : UndoLog {
  override val indicesComparator: Comparator<UndoGroupReference>
    get() = compareBy { it.undoGroupPosition }

  override fun last(): CustomIndexedValue<UndoGroupReference, DocumentUndoGroup>? {
    return if (undoStack.isEmpty()) null
    else CustomIndexedValue(UndoGroupReference(id, undoStack.size() - 1), undoStack.last())
  }

  override fun previous(reference: UndoGroupReference): IndexedUndoGroup? {
    require(reference.logData == id)
    return if (reference.undoGroupPosition == 0L) null
    else IndexedUndoGroup(UndoGroupReference(id, reference.undoGroupPosition - 1), undoStack.nth(reference.undoGroupPosition - 1))
  }

  override fun next(reference: UndoGroupReference): IndexedUndoGroup? {
    require(reference.logData == id)
    return if (reference.undoGroupPosition == undoStack.size() - 1) null
    else IndexedUndoGroup(UndoGroupReference(id, reference.undoGroupPosition + 1), undoStack.nth(reference.undoGroupPosition + 1))
  }

  override fun resolve(reference: UndoGroupReference): DocumentUndoGroup {
    return nth(reference.undoGroupPosition)
  }

  override fun computeComposition(edits: EditLog, fromTimestamp: Long, toTimestamp: Long): Operation {
    var result = with(undoBaseOperationCache) {
      when {
        this != null && fromTimestamp <= fromEdit && toTimestamp >= toEdit -> {
          val head = edits.slice(fromTimestamp, fromEdit).compose()
          val tail = edits.slice(toEdit, toTimestamp).compose()
          listOf(head, operation, tail).compose()
        }
        else -> {
          edits.slice(fromTimestamp, toTimestamp).compose()
        }
      }
    }
    result = result.normalizeHard()
    undoBaseOperationCache = CompositionCache(result, fromTimestamp, toTimestamp)
    return result
  }

  val size: Long
    get() = undoStack.size()

  fun slice(from: Long, to: Long): IBifurcanVector<DocumentUndoGroup> =
    undoStack.slice(from, to)

  operator fun get(timestamp: Long): DocumentUndoGroup =
    undoStack.nth(timestamp)

  companion object {
    val EMPTY = UndoLogData(
      id = SameUndoLogDataReferenceType.reference(SameUndoLogDataReference),
      undoStack = BifurcanVector(),
      openForMerge = true,
      undoBaseOperationCache = null
    )
  }

  data class UndoLogChange(
    val addedGroup: DocumentUndoGroup?,
    val index: Long,
    val openForMerge: Boolean,
  ) {
    fun apply(undoLog: UndoLogData): UndoLogData {
      if (this.addedGroup == null) {
        return UndoLogData(
          id = undoLog.id,
          openForMerge = this.openForMerge,
          undoStack = undoLog.undoStack,
          undoBaseOperationCache = undoLog.undoBaseOperationCache
        )
      }
      else {
        return UndoLogData(
          id = undoLog.id,
          undoStack = undoLog.undoStack.set(this.index, this.addedGroup),
          openForMerge = this.openForMerge,
          undoBaseOperationCache = undoLog.undoBaseOperationCache
        )
      }
    }
  }

  fun closeForMerge(): UndoLogData {
    return UndoLogData(id = id,
                       undoStack = undoStack,
                       openForMerge = false,
                       undoBaseOperationCache = undoBaseOperationCache)
  }

  fun nth(idx: Long): DocumentUndoGroup {
    return undoStack.nth(idx)
  }

  fun take(sizeToTrim: Long): UndoLogData {
    return UndoLogData(id = id, undoStack = undoStack.slice(0, sizeToTrim), openForMerge = false, undoBaseOperationCache = undoBaseOperationCache)
  }

}

@Serializable
data class UndoLogDataReference(val typeId: TypeId, val value: SerializedValue) {
  @Serializable
  @JvmInline
  value class TypeId(val type: String)

  data class Type<T>(val typeId: TypeId, val serializer: KSerializer<T>) {
    fun reference(value: T): UndoLogDataReference =
      UndoLogDataReference(typeId, SerializedValue.fromDeserializedValue(value, serializer))
  }

  fun <T> isa(type: Type<T>): T? =
    if (this.typeId == type.typeId) value.get(type.serializer) else null
}

@Serializable
data class DocumentUndoLogDataReference(val editLogId: UID) {
  companion object {
    val Type = UndoLogDataReference.Type(
      UndoLogDataReference.TypeId(DocumentUndoLogDataReference::class.qualifiedName!!),
      serializer()
    )
  }
}

/**
 * For using in simple undo model (for documents that cannot be referenced)
 */
@Serializable
data object SameUndoLogDataReference

val SameUndoLogDataReferenceType = UndoLogDataReference.Type(
  UndoLogDataReference.TypeId(SameUndoLogDataReference::class.qualifiedName!!),
  SameUndoLogDataReference.serializer()
)

@Serializable
data class CustomIndexedValue<I, out T>(val index: I, val value: T)

@Serializable
data class UndoGroupReference(val logData: UndoLogDataReference, val undoGroupPosition: Long) {
  constructor(editLogId: UID, undoGroupPosition: Long) : this(DocumentUndoLogDataReference.Type.reference(DocumentUndoLogDataReference(editLogId)), undoGroupPosition)
}

typealias GlobalUndoLogRef = Long

val GlobalUndoLogRef.position: Long get() = this

data class GlobalUndoInfo(
  // grouped by enclosing undo log, sorted within log
  val includedGroupsByLog: List<List<UndoGroupReference>>,
)
