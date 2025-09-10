// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.icons

import com.intellij.icons.AllIcons
import com.intellij.ide.rpc.deserializeFromRpc
import com.intellij.ide.rpc.serializeToRpc
import com.intellij.openapi.diagnostic.fileLogger
import fleet.util.openmap.SerializedValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

private val LOG = fileLogger()

/**
 * Converts an [Icon] instance into an [IconId] which can be used in RPC calls and stored in Rhizome.
 *
 * **WARNING: This API is experimental and should be used with care.**
 *
 * In the monolith version of the IDE, this essentially stores a reference to the original icon object.
 * In Remote Development scenarios, the icon is serialized for transmission to the frontend.
 *
 *  Important limitations:
 * - **Might not work for custom icon classes** - serialization may fail for custom Icon implementations
 * - **Recommended approach:** Use default icon classes provided by the platform API
 *   instead of custom implementations for better compatibility
 * - Custom icons may not serialize/deserialize properly across RPC boundaries
 *

 * @return An [IconId] that can be used in RPC calls
 * @throws Exception if the icon cannot be serialized (use [rpcIdOrNull] for safer handling)
 */
@ApiStatus.Experimental
fun Icon.rpcId(): IconId {
  val icon = this
  val serializedIcon = serializeToRpc(icon)

  return IconId(serializedIcon, icon)
}

/**
 * Converts an [Icon] instance into an [IconId] which can be used in RPC calls and stored in Rhizome.
 * 
 * **WARNING: This API is experimental and should be used with care.**
 *
 * This is a safer version of [rpcId] that returns null instead of throwing an exception when serialization fails.
 *
 * @return An [IconId] that can be used in RPC calls, or null if serialization fails
 */
@ApiStatus.Experimental
fun Icon.rpcIdOrNull(): IconId? {
  val icon = this

  val serializedIcon = try {
    serializeToRpc(icon)
  } catch (e: Exception) {
    LOG.debug("Cannot serialize icon $icon", e)
    return null
  }

  return IconId(serializedIcon, icon)
}

/**
 * Retrieves the [Icon] associated with the given [IconId].
 *
 * **WARNING: This API is experimental and should be used with care.**
 *
 * In the monolith version of the IDE, this method essentially does nothing - it just reuses the original
 * icon object that was passed to [rpcId]. However, in distributed scenarios (Remote Development), this
 * function attempts to deserialize an Icon from RPC data.
 *
 * Important limitations:
 * - **Might not work for custom icon classes** - deserialization may fail for custom Icon implementations
 * - **Recommended approach:** Use default icon classes provided by the platform API for better compatibility and reliability
 * - Falls back to [AllIcons.Empty] if deserialization fails
 *
 * @return The [Icon] if available, or [AllIcons.Empty] if deserialization fails
 */
@ApiStatus.Experimental
fun IconId.icon(): Icon {
  if (localIcon != null) {
    return localIcon
  }

  return deserializeFromRpc<Icon>(serializedValue) ?: run {
    LOG.debug("Cannot deserialize icon from a remote model, empty icon is used instead.")
    AllIcons.Empty
  }
}

@ApiStatus.Experimental
@Serializable
class IconId internal constructor(
  @Serializable @JvmField internal val serializedValue: SerializedValue? = null,
  @Transient @JvmField internal val localIcon: Icon? = null,
)