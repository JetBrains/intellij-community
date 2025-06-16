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
 * Converts an [Icon] instance into a [IconId] which can be used in RPC calls and stored in Rhizome.
 */
@ApiStatus.Internal
fun Icon.rpcId(): IconId {
  val icon = this
  val serializedIcon = serializeToRpc(icon)

  return IconId(serializedIcon, icon)
}

@ApiStatus.Internal
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
 */
@ApiStatus.Internal
fun IconId.icon(): Icon {
  if (localIcon != null) {
    return localIcon
  }

  return deserializeFromRpc<Icon>(serializedValue) ?: run {
    LOG.debug("Cannot deserialize icon from a remote model, empty icon is used instead.")
    AllIcons.Empty
  }
}

@ApiStatus.Internal
@Serializable
class IconId internal constructor(
  @Serializable @JvmField internal val serializedValue: SerializedValue? = null,
  @Transient @JvmField internal val localIcon: Icon? = null,
)