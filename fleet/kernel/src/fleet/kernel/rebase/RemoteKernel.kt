// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rebase

import fleet.kernel.DurableSnapshot
import fleet.kernel.DurableDbValue
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.core.RpcFlow
import fleet.rpc.core.InstanceId
import fleet.util.UID
import fleet.util.openmap.SerializedValue
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

val REMOTE_KERNEL_SERVICE_ID: InstanceId = InstanceId("fleet.reserved.RemoteKernel")

@Rpc
interface RemoteKernel : RemoteApi<Unit> {
  @Serializable
  data class Subscription(
    val snapshot: RpcFlow<DurableSnapshot.DurableEntity>,
    val vectorClock: Map<UID, Long>,
    val txs: RpcFlow<Broadcast>,
  )

  @Serializable
  sealed interface Broadcast {
    @Serializable
    @SerialName("tx")
    data class Tx(val transaction: Transaction) : Broadcast

    @Serializable
    @SerialName("failure")
    data class Failure(val origin: UID, val transactionId: UID) : Broadcast

    @Serializable
    @SerialName("ack")
    data class Ack(val transactionId: UID) : Broadcast

    @Serializable
    @SerialName("rejection")
    data class Rejection(val transactionId: UID) : Broadcast

    @Serializable
    @SerialName("reset")
    data object Reset : Broadcast
  }

  suspend fun subscribe(author: UID?): Subscription
  suspend fun transact(frontendTxs: ReceiveChannel<Transaction>)
}


@Serializable
data class Transaction(
  val id: UID,
  val instructions: List<SharedInstruction>,
  val origin: UID,
  val index: Long,
)

@Serializable
data class SharedInstruction(
  val name: String,
  val instruction: SerializedValue,
)

@Serializable
sealed interface SharedQuery {
  @Serializable
  data class Entity(val uid: UID) : SharedQuery

  @Serializable
  data class GetOne(val uid: UID, val attribute: String) : SharedQuery

  @Serializable
  data class GetMany(val uid: UID, val attribute: String) : SharedQuery

  @Serializable
  data class LookupUnique(val attribute: String, val value: DurableDbValue) : SharedQuery

  @Serializable
  data class LookupMany(val attribute: String, val value: DurableDbValue) : SharedQuery

  @Serializable
  data class Column(val attribute: String) : SharedQuery

  @Serializable
  data class RefsTo(val uid: UID) : SharedQuery

  @Serializable
  data class Contains(val uid: UID, val attribute: String, val value: DurableDbValue) : SharedQuery
}

@Serializable
data class SharedValidate(
  val q: SharedQuery,
  val trace: Long,
)
