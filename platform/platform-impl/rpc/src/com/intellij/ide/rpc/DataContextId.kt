// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.rpc

import com.intellij.openapi.actionSystem.DataContext
import fleet.util.openmap.SerializedValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

/**
 * Converts a [DataContext] instance into a [DataContextId] which can be used in RPC calls and stored in Rhizome.
 *
 * **WARNING: Use with extreme care, and preferably avoid using this function at all.**
 *
 * In the monolith version of the IDE, this essentially stores a reference to the original DataContext object.
 * In Remote Development scenarios, this function attempts to serialize the entire DataContext for RPC transmission,
 * but **not all elements of DataContext can be properly sent/retrieved** using this mechanism.
 * Many DataContext values may:
 * - Not be serializable
 * - Reference local IDE components that don't exist on the remote side
 * - Contain complex objects that can't be safely transmitted
 *
 * **Recommended approach:** Instead of using this function, manually send only the specific data you need
 * via RPC. This ensures better reliability, performance, and maintainability.
 *
 * **For custom data types:** If you need to serialize custom DataContext elements, implement
 * [com.intellij.ide.CustomDataContextSerializer] for your specific data types.
 *
 * @return A [DataContextId] that can be used in RPC calls, but may not contain all original DataContext elements
 */
@ApiStatus.Experimental
fun DataContext.rpcId(): DataContextId {
  val context = this
  val serializedContext = serializeToRpc(context)

  return DataContextId(serializedContext, context)
}

/**
 * Retrieves the [DataContext] associated with the given [DataContextId].
 *
 * **WARNING: Use with extreme care, and preferably avoid using this function at all.**
 *
 * In the monolith version of the IDE, this method essentially does nothing - it just reuses the original
 * object that was passed to [rpcId]. However, in distributed scenarios (Remote Development), this function
 * attempts to deserialize a DataContext from RPC data, but **not all elements of DataContext can be properly
 * sent/retrieved** using this mechanism. The retrieved DataContext may:
 * - Be missing important data that couldn't be serialized
 * - Contain stale or invalid references
 * - Not reflect the current state of the IDE
 * - Fail to deserialize complex objects properly
 *
 * **Recommended approach:** Instead of using this function, manually send only the specific data you need
 * via RPC and work with that data directly. This ensures better reliability, performance, and maintainability.
 *
 * **For custom data types:** If you need to deserialize custom DataContext elements, implement
 * [com.intellij.ide.CustomDataContextSerializer] for your specific data types.
 *
 * @return The deserialized [DataContext] if available, or null if deserialization fails or no context is available.
 *         Note that even a non-null result may not contain all expected DataContext elements.
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