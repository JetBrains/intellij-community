// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.serialization.DataSerializer
import io.lacuna.bifurcan.IMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer

class BifurcanMapSerializer<K, V>(keySer: KSerializer<K>, valSer: KSerializer<V>) : DataSerializer<IMap<K, V>, Map<K, V>>(MapSerializer(keySer, valSer)) {
  override fun fromData(data: Map<K, V>): IMap<K, V> {
    return io.lacuna.bifurcan.Map.from(data)
  }

  override fun toData(value: IMap<K, V>): Map<K, V> {
    return value.toMap()
  }
}