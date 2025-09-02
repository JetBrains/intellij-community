// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.serialization.DataSerializer
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer

class KPersistentMapSerializer<K, V>(keySer: KSerializer<K>, valSer: KSerializer<V>) : DataSerializer<PersistentMap<K, V>, Map<K, V>>(MapSerializer(keySer, valSer)) {
  override fun fromData(data: Map<K, V>): PersistentMap<K, V> {
    return data.toPersistentHashMap()
  }

  override fun toData(value: PersistentMap<K, V>): Map<K, V> {
    return value.toMap()
  }
}