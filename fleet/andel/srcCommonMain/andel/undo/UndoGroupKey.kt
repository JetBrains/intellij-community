// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.undo

import fleet.openmap.SerializableKey

// it is not a key at all, it is never compared or looked up
// its only purpose is to be converted into UndoGroupAttributes
interface UndoGroupKey {
  fun toAttributes(): UndoGroupAttributes
  operator fun <T : Any> get(attribute: SerializableKey<T, UndoGroupAttributes>): T? =
    this.toAttributes().map[attribute]
}

object TypeUndoGroupKey : UndoGroupKey {
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