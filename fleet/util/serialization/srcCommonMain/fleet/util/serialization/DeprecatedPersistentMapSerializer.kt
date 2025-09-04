// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.serialization

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.PairSerializer

@Deprecated("Use PersistentMapSerializer instead. Note that the new serializer is not backward-compatible with the old one.", ReplaceWith("PersistentMapSerializer(keySerializer, valueSerializer)"))
class DeprecatedPersistentMapSerializer<K, V>(
  keySerializer: KSerializer<K>,
  valueSerializer: KSerializer<V>
): DataSerializer<PersistentMap<K, V>, List<Pair<K, V>>>(ListSerializer(PairSerializer(keySerializer, valueSerializer))) {
  override fun fromData(data: List<Pair<K, V>>): PersistentMap<K, V> {
    return data.toMap().toPersistentHashMap()
  }

  override fun toData(value: PersistentMap<K, V>): List<Pair<K, V>> {
    return value.entries.map { Pair(it.key, it.value) }
  }
}