// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.rpc

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.extensions.ExtensionPointName
import fleet.util.openmap.SerializedValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

private val LOG = fileLogger()

@ApiStatus.Internal
interface AnActionSerializer {
  fun serializeToRpc(action: AnAction, scope: CoroutineScope): SerializedValue?
  fun deserializeFromRpc(serializedValue: SerializedValue): AnAction?

  companion object {
    internal val EP_NAME = ExtensionPointName<AnActionSerializer>("com.intellij.anActionSerializer")
  }
}


/**
 * Converts an [AnAction] instance into an [AnActionId] which can be used in RPC calls.
 *
 * In the monolith version of the IDE, this essentially stores a reference to the original action object.
 * In Remote Development scenarios, the action is serialized for transmission to the frontend by an
 * [AnActionSerializer] implementation.
 *
 * @param cs [CoroutineScope] that limits the validity of the returned [AnActionId]. When the scope is cancelled,
 * the action will no longer be available for RPC calls via the returned ID.
 * @return An [AnActionId] that can be used in RPC calls
 */
@ApiStatus.Experimental
fun AnAction.rpcId(cs: CoroutineScope): AnActionId {
  val serializedValue = AnActionSerializer.EP_NAME.computeSafeIfAny { it.serializeToRpc(this, cs) }
  return AnActionId(serializedValue, this)
}

/**
 * Retrieves the [AnAction] associated with the given [AnActionId].
 *
 * In the monolith version of the IDE, this method essentially does nothing - it just reuses the original
 * action object that was passed to [rpcId]. However, in distributed scenarios (Remote Development), this
 * function attempts to deserialize an action from RPC data using an [AnActionSerializer].
 *
 * @return The [AnAction] if available, or null if deserialization fails or the action cannot be found
 */
@ApiStatus.Experimental
fun AnActionId.action(): AnAction? {
  if (localAction != null) {
    return localAction
  }

  return serializedValue?.let { value ->
    AnActionSerializer.EP_NAME.computeSafeIfAny { it.deserializeFromRpc(value) }
  } ?: run {
    LOG.debug("Cannot deserialize AnActionId($this)")
    null
  }
}

@ApiStatus.Experimental
@Serializable
class AnActionId internal constructor(
  @Serializable internal val serializedValue: SerializedValue? = null,
  @Transient internal val localAction: AnAction? = null,
)