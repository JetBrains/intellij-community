// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.serialization.DataSerializer
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

class PersistentListSerializer<T>(elementSer: KSerializer<T>): DataSerializer<PersistentList<T>, List<T>>(ListSerializer(elementSer)) {
  override fun fromData(data: List<T>): PersistentList<T> {
    return data.toPersistentList()
  }

  override fun toData(value: PersistentList<T>): List<T> {
    return value
  }
}