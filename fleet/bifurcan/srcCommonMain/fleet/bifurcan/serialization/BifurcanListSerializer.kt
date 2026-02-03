// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.bifurcan.serialization

import fleet.bifurcan.BifurcanVector
import fleet.bifurcan.IBifurcanVector
import fleet.util.serialization.DataSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

data class BifurcanListSerializer<T>(val elementSer: KSerializer<T>): DataSerializer<IBifurcanVector<T>, List<T>>(
  ListSerializer(elementSer)) {
  override fun fromData(data: List<T>): BifurcanVector<T> {
    return BifurcanVector.from(data)
  }

  override fun toData(value: IBifurcanVector<T>): List<T> {
    return value.toList()
  }
}