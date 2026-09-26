// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel.ids

import com.intellij.platform.rpc.Id
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.ApiStatus

/**
 * Transforms a flow of backend objects into a [Flow] of [Id]s which can be passed through RPC.
 * Backend object may be acquired from [Id] by [findValueById] function later on.
 *
 * This API is useful when you need to pass backend based [Flow] to a client through RPC.
 * So, later the client can make requests based on these IDs.
 *
 * When [Flow] receives new value, previously associated [Id] will be deleted
 * and value couldn't be acquired by this [Id].
 *
 * @see storeValueGlobally
 */
@ApiStatus.Internal
fun <TID : Id, Value : Any> Flow<Value>.asIDsFlow(type: BackendValueIdType<TID, Value>): Flow<TID> {
  val flow = this
  return channelFlow {
    flow.collectLatest { value ->
      coroutineScope {
        val id = storeValueGlobally(this, value, type)
        send(id)
        awaitCancellation()
      }
    }
  }
}

/**
 * Transforms a flow of backend objects into a [Flow] of [Id]s which can be passed through RPC.
 * Backend object may be acquired from [Id] by [findValueById] function later on.
 *
 * This API is useful when you need to pass backend based [Flow] to a client through RPC.
 * So, later the client can make requests based on these IDs.
 *
 * When [Flow] receives new value, previously associated [Id] will be deleted
 * and value couldn't be acquired by this [Id].
 *
 * @see storeValueGlobally
 */
@ApiStatus.Internal
fun <TID : Id, Value : Any> Flow<Value?>.asNullableIDsFlow(type: BackendValueIdType<TID, Value>): Flow<TID?> {
  return this.withNullableIDsFlow(type) { id, _ -> id }
}

/**
 * Transforms a flow of backend objects into a [Flow] of objects returned by [mapFunction].
 * First argument of [mapFunction] is object's [Id] which can be passed through RPC.
 * Second argument is the current value of the given [Flow].
 *
 * Backend object may be acquired from [Id] by [findValueById] function later on.
 * This API is useful when you need to pass backend based [Flow] to a client through RPC.
 * So, later the client can make requests based on these IDs.
 *
 * When [Flow] receives new value, previously associated [Id] will be deleted
 * and value couldn't be acquired by this [Id].
 *
 * @see storeValueGlobally
 */
@ApiStatus.Internal
fun <TID : Id, Value : Any, K> Flow<Value?>.withNullableIDsFlow(
  type: BackendValueIdType<TID, Value>,
  mapFunction: (TID?, Value?) -> K,
): Flow<K> {
  val flow = this
  return channelFlow {
    flow.collectLatest { value ->
      if (value == null) {
        send(mapFunction(null, value))
        return@collectLatest
      }
      coroutineScope {
        val id = storeValueGlobally(this, value, type)
        send(mapFunction(id, value))
        awaitCancellation()
      }
    }
  }
}