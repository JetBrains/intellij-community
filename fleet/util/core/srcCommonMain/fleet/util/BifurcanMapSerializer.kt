// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.bifurcan.SortedMap
import fleet.util.serialization.DataSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer

class BifurcanMapSerializer<K: Comparable<K>, V>(keySer: KSerializer<K>, valSer: KSerializer<V>) : DataSerializer<SortedMap<K, V>, Map<K, V>>(MapSerializer(keySer, valSer)) {
  override fun fromData(data: Map<K, V>): SortedMap<K, V> {
    return SortedMap.from<K, V>(data)
  }

  override fun toData(value: SortedMap<K, V>): Map<K, V> {
    return value.toMap()
  }
}