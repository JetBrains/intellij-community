// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel.backend

import com.jetbrains.rhizomedb.EID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.ApiStatus

/**
 * Transforms a flow of backend objects into a [Flow] of IDs which can be passed through RPC.
 * Backend object may be acquired from [EID] by [findValueEntity] function later on.
 *
 * This API is useful when you need to pass backend based [Flow] to a client through RPC.
 * So, later the client can make requests based on these IDs.
 */
@ApiStatus.Internal
fun <T : Any> Flow<T>.asIDsFlow(): Flow<EID> {
  val flow = this
  return channelFlow {
    flow.collectLatest { value ->
      val valueEntity = newValueEntity(value)
      try {
        send(valueEntity.id)
        awaitCancellation()
      }
      finally {
        valueEntity.delete()
      }
    }
  }
}

/**
 * Transforms a flow of backend objects into a [Flow] of IDs which can be passed through RPC.
 * Backend object may be acquired from [EID] by [findValueEntity] function later on.
 *
 * This API is useful when you need to pass backend based [Flow] to a client through RPC.
 * So, later the client can make requests based on these IDs.
 */
@ApiStatus.Internal
fun <T : Any?> Flow<T>.asNullableIDsFlow(): Flow<EID?> {
  return this.withNullableIDsFlow { eid, _ -> eid }
}

/**
 * Transforms a flow of backend objects into a [Flow] of objects returned by [mapFunction].
 * First argument of [mapFunction] is object's ID which can be passed through RPC.
 * Second argument is the current value of the given [Flow].
 *
 * Backend object may be acquired from [EID] by [findValueEntity] function later on.
 *
 * This API is useful when you need to pass backend based [Flow] to a client through RPC.
 * So, later the client can make requests based on these IDs.
 */
@ApiStatus.Internal
fun <T : Any?, K : Any?> Flow<T>.withNullableIDsFlow(mapFunction: (EID?, T) -> K): Flow<K> {
  val flow = this
  return channelFlow {
    flow.collectLatest { value ->
      if (value == null) {
        send(mapFunction(null, value))
        return@collectLatest
      }
      val valueEntity = newValueEntity(value)
      try {
        send(mapFunction(valueEntity.id, value))
        awaitCancellation()
      }
      finally {
        valueEntity.delete()
      }
    }
  }
}