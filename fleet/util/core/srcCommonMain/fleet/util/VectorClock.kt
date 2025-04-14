// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

const val CompressedVectorClockKey = "CompressedVectorClock"

@Serializable
data class CompressedVectorClock(val clientId: UID,
                                 val consumedTxs: Long,
                                 val sentTxs: Long) {
  companion object {
    val Zero: CompressedVectorClock = CompressedVectorClock(UID.fromString("00000000000000000000"), 0L, 0L)
  }
}

data class LocalDbTimestamp(val kernelId: Any,
                            val timestamp: Long)

@Serializable
data class Causal<T>(val value: T,
                     val clock: CompressedVectorClock,
                     @Transient
                     val localDbTimestamp: LocalDbTimestamp? = null)

data class VectorClock(val clock: PersistentMap<UID, Long>) {
  companion object {
    val Zero: VectorClock = VectorClock(persistentHashMapOf())

    fun fromMap(map: Map<UID, Long>): VectorClock {
      return VectorClock(map.toPersistentHashMap())
    }
  }

  fun compress(clientId: UID): CompressedVectorClock {
    return CompressedVectorClock(clientId = clientId,
                                 consumedTxs = clock.entries.sumOf { e ->
                                   when {
                                     e.key != clientId -> e.value
                                     else -> 0L
                                   }
                                 },
                                 sentTxs = clock.get(clientId) ?: 0L)
  }

  fun tick(clientId: UID): VectorClock {
    return VectorClock(clock.update(clientId) { c -> (c ?: 0L) + 1 })
  }
}

fun CompressedVectorClock.precedesOrEqual(vectorClock: VectorClock): Boolean {
  val compressed = vectorClock.compress(this.clientId)
  return this.consumedTxs <= compressed.consumedTxs && this.sentTxs <= compressed.sentTxs
}