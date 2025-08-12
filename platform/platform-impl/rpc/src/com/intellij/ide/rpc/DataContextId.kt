// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.rpc

import com.intellij.openapi.actionSystem.DataContext
import fleet.util.openmap.SerializedValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

/**
 * Converts an [DataContext] instance into a [DataContextId] which can be used in RPC calls and stored in Rhizome.
 */
@ApiStatus.Experimental
fun DataContext.rpcId(): DataContextId {
  val context = this
  val serializedContext = serializeToRpc(context)

  return DataContextId(serializedContext, context)
}

/**
 * Retrieves the [DataContext] associated with the given [DataContextId].
 */
@ApiStatus.Experimental
fun DataContextId.dataContext(): DataContext? {
  if (localContext != null) {
    return localContext
  }

  return deserializeFromRpc<DataContext>(serializedValue)
}

@ApiStatus.Experimental
@Serializable
class DataContextId internal constructor(
  @Serializable @JvmField internal val serializedValue: SerializedValue? = null,
  @Transient @JvmField internal val localContext: DataContext? = null,
)