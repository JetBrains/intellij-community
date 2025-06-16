// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc

import fleet.rpc.core.*
import fleet.util.cast
import fleet.util.letIf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable

/**
 * Base interface that must be implemented by every Fleet service.
 *
 * [Metadata] class parameter must be a [kotlinx.serialization.Serializable].
 *
 * A Fleet service without any metadata to expose must use [Unit] as [Metadata] type.
 */
interface RemoteApi<Metadata>

/**
 * Every RPC interface must be annotated with [Rpc] annotation.
 * This annotation requires the rpc compiler plugin for code generation.
 */
@Target(AnnotationTarget.CLASS)
annotation class Rpc

sealed interface RemoteKind {
  data class Data(val serializer: KSerializer<*>) : RemoteKind
  data class Flow(val elementKind: RemoteKind, val nullable: Boolean) : RemoteKind
  data class ReceiveChannel(val elementKind: RemoteKind, val nullable: Boolean) : RemoteKind
  data class SendChannel(val elementKind: RemoteKind, val nullable: Boolean) : RemoteKind
  data class Deferred(val elementKind: RemoteKind, val nullable: Boolean) : RemoteKind
  data class RemoteObject(val descriptor: RemoteApiDescriptor<*>) : RemoteKind
  data class Resource(val descriptor: RemoteApiDescriptor<*>) : RemoteKind
}

fun RemoteKind.serializer(debugInfo: String): KSerializer<Any?> {
  return when (this) {
    is RemoteKind.Data -> serializer
    is RemoteKind.Flow -> @Suppress("DEPRECATION") FlowSerializer(elementKind.serializer(debugInfo)).letIf(nullable) { it.nullable }
    is RemoteKind.ReceiveChannel -> ReceiveChannelSerializer(elementKind.serializer(debugInfo)).letIf(nullable) { it.nullable }
    is RemoteKind.SendChannel -> SendChannelSerializer(elementKind.serializer(debugInfo)).letIf(nullable) { it.nullable }
    is RemoteKind.Deferred -> DeferredSerializer(elementKind.serializer(debugInfo)).letIf(nullable) { it.nullable }
    is RemoteKind.RemoteObject -> error("Remote object has no serializer")
    is RemoteKind.Resource -> error("Resource has no serializer")
  }.cast()
}

data class ParameterDescriptor(val parameterName: String, val parameterKind: RemoteKind)
data class RpcSignature(val methodName: String, val parameters: Array<ParameterDescriptor>, val returnType: RemoteKind)

interface RemoteApiDescriptor<T : RemoteApi<*>> {
  fun getSignature(methodName: String): RpcSignature
  fun clientStub(proxy: suspend (String, Array<Any?>) -> Any?): T
  fun getApiFqn(): String
  suspend fun call(impl: T, methodName: String, args: Array<Any?>): Any?
}

/**
 * Intrinsic that returns [RemoteApiDescriptor].
 * Parameter [descriptor] is provided by rpc compiler plugin in compile time by the type argument.
 * This function requires rpc compiler plugin to work properly, otherwise throws [IllegalStateException] in runtime.
 */
inline fun <reified T : RemoteApi<*>> remoteApiDescriptor(descriptor: RemoteApiDescriptor<T>? = null): RemoteApiDescriptor<T> {
  return descriptor ?: error("Couldn't get remoteApiDescriptor for ${T::class}, enable RPC plugin")
}
