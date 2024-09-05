// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.serialization.DataSerializer
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.PairSerializer

class PersistentMapSerializer<K, V>(
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