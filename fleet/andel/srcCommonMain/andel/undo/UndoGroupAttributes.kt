// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.undo

import fleet.openmap.SerializableKey
import fleet.openmap.SerializableOpenMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

// it has several responsibilities at the same time:
// - contains well-known attributes that are consumed by andel.undo itself (like `mergeKey` or `includeIntoLog`)
// - serves as an extension mechanism for the platform, see UndoOperationsDataKey
// - records undo
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

    fun empty() = UndoGroupAttributes(SerializableOpenMap.Companion.empty())

    val description = SerializableKey<String, UndoGroupAttributes>("undoGroup.description", String.serializer())
    val mergeKey = SerializableKey<String, UndoGroupAttributes>("undoGroup.merge.key", String.serializer())
    val mergeAlways = SerializableKey<Boolean, UndoGroupAttributes>("undoGroup.merge.always", Boolean.serializer())

    // default: include if contains some changes
    val includeIntoLog = SerializableKey<Boolean, UndoGroupAttributes>("undoGroup.includeIntoLog", Boolean.serializer())
  }
}