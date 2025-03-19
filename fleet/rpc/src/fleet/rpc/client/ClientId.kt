// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.client

import fleet.util.UID
import fleet.util.serialization.DataSerializer
import kotlinx.serialization.Serializable

@Serializable(with = ClientIdSerializer::class)
data class ClientId(val uid: UID)

internal class ClientIdSerializer : DataSerializer<ClientId, UID>(UID.serializer()) {
  override fun fromData(data: UID): ClientId = ClientId(data)
  override fun toData(value: ClientId): UID = value.uid
}
