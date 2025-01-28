// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.undo

import fleet.util.openmap.SerializableKey

interface UndoGroupKey {
  fun toAttributes(): UndoGroupAttributes
  operator fun <T : Any> get(attribute: SerializableKey<T, UndoGroupAttributes>): T? =
    this.toAttributes().map[attribute]
}

data class UndoUndoGroupKey(val reverted: List<UndoGroupReference>, val description: String?) : UndoGroupKey {
  override fun toAttributes(): UndoGroupAttributes {
    return UndoGroupAttributes
      .with(UndoGroupAttributes.undo, reverted)
      .with(UndoGroupAttributes.includeIntoLog, true)
      .withNotNull(UndoGroupAttributes.description, description)
  }

}

data class RedoUndoGroupKey(val reverted: List<UndoGroupReference>, val description: String?) : UndoGroupKey {
  override fun toAttributes(): UndoGroupAttributes {
    return UndoGroupAttributes
      .with(UndoGroupAttributes.redo, reverted)
      .with(UndoGroupAttributes.includeIntoLog, true)
      .withNotNull(UndoGroupAttributes.description, description)
  }

}

object TypeUndoGroupKey: UndoGroupKey {
  override fun toAttributes(): UndoGroupAttributes {
    return UndoGroupAttributes.with(UndoGroupAttributes.mergeKey, "undoGroup.type").with(UndoGroupAttributes.includeIntoLog, true)
  }
}

object DeleteUndoGroupKey : UndoGroupKey {
  override fun toAttributes(): UndoGroupAttributes {
    return UndoGroupAttributes.with(UndoGroupAttributes.mergeKey, "undoGroup.delete")
  }
}

object DefaultUndoGroupKey : UndoGroupKey {
  override fun toAttributes(): UndoGroupAttributes {
    return UndoGroupAttributes.empty()
  }

}

internal object NavigationUndoGroupKey : UndoGroupKey {
  override fun toAttributes(): UndoGroupAttributes {
    return UndoGroupAttributes.with(UndoGroupAttributes.includeIntoLog, false)
  }
}

/**
 * Marker interface for labels that group undo groups into blocks
 */
interface UndoBlockStart : UndoGroupKey

interface UndoBlockEnd : UndoGroupKey {
  val otherBlockElements: List<UndoGroupReference>
}