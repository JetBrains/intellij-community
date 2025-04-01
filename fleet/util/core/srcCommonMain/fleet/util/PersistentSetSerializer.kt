// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.serialization.DataSerializer
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.toPersistentHashSet
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.SetSerializer

class PersistentSetSerializer<T>(elementSer: KSerializer<T>): DataSerializer<PersistentSet<T>, Set<T>>(SetSerializer(elementSer)) {
  override fun fromData(data: Set<T>): PersistentSet<T> {
    return data.toPersistentHashSet()
  }

  override fun toData(value: PersistentSet<T>): Set<T> {
    return value
  }
}