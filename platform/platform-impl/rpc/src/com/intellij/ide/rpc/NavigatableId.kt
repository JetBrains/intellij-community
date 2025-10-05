// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.rpc

import com.intellij.navigation.EmptyNavigatable
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.pom.Navigatable
import fleet.util.openmap.SerializedValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

private val LOG = fileLogger()

/**
 * Converts a [Navigatable] instance into a [NavigatableId] which can be used in RPC calls.
 *
 * Note that the returned instance keeps a strong reference to the original [Navigatable],
 * and when used locally, [navigatable] will return that same original instance.
 * But, when used over RPC, the link between the original instance and the de-serialized instance will only work as long as
 * the original instance is kept from being GC-ed by the calling code. Generation of [NavigatableId] doesn't prevent the collection
 * of the [Navigatable] instance by itself. So, for the remote case, this is similar to a weak reference.
 */
@ApiStatus.Internal
fun Navigatable.weakRpcId(): NavigatableId {
  val serialized = serializeToRpc(this)
  return NavigatableId(serialized, this)
}

/**
 * Retrieves the [Navigatable] instance associated with the given [NavigatableId].
 */
@ApiStatus.Internal
fun NavigatableId.navigatable(): Navigatable {
  if (localNavigatable != null) {
    return localNavigatable
  }
  return deserializeFromRpc<Navigatable>(serializedValue) ?: run {
    LOG.debug("Cannot deserialize NavigatableId($this), using EmptyNavigatable")
    EmptyNavigatable.INSTANCE
  }
}

/**
 * A representation of [Navigatable] that can be passed over RPC.
 *
 * @see
 */
@ApiStatus.Internal
@Serializable
@ConsistentCopyVisibility
data class NavigatableId internal constructor(internal val serializedValue: SerializedValue?,
                                              @Transient internal val localNavigatable: Navigatable? = null)