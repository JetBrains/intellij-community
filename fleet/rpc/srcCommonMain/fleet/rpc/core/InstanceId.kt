// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.core

import fleet.util.UID
import fleet.util.serialization.DataSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

/**
 * Opaque identifier of a remote service.
 * Should be unique between all providers connected to the workspace.
 * Does not have any restrictions on its length.
 * */
@Serializable(with = InstanceIdSerializer::class)
data class InstanceId(val id: String) {
  companion object {
    fun random(): InstanceId = InstanceId(UID.random().toString())
  }
}

internal class InstanceIdSerializer : DataSerializer<InstanceId, String>(String.serializer()) {
  override fun fromData(data: String): InstanceId = InstanceId(data)
  override fun toData(value: InstanceId): String = value.id
}
