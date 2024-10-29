// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.core

import fleet.util.serialization.DataSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

@Serializable(with=RpcFlow.Serializer::class)
data class RpcFlow<T> internal constructor(internal val flow: Flow<T>) {
  companion object {
    fun<T> empty(): RpcFlow<T> = RpcFlow(emptyFlow())
    fun<T> of(vararg ts: T) = RpcFlow(flowOf(*ts))
  }

  @Suppress("DEPRECATION")
  internal class Serializer<T>(elementSerializer: KSerializer<T>): DataSerializer<RpcFlow<T>, Flow<T>>(FlowSerializer(elementSerializer)) {
    override fun fromData(data: Flow<T>): RpcFlow<T> = RpcFlow(data)
    override fun toData(value: RpcFlow<T>): Flow<T> = value.flow
  }

  fun toFlow(): Flow<T> = flow
}

class FlowScope(val scope: CoroutineScope): CoroutineContext.Element {
  companion object: CoroutineContext.Key<FlowScope>

  override val key: CoroutineContext.Key<*> get() = FlowScope
}

suspend fun<T> Flow<T>.toRpc(): RpcFlow<T> = toRpc(coroutineContext)

fun<T> Flow<T>.toRpc(context: CoroutineContext): RpcFlow<T> = RpcFlow(flowOn(context.minusKey(Job)))
