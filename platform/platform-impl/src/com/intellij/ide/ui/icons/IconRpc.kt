// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.icons

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.extensions.ExtensionPointName
import fleet.util.openmap.SerializedValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

private val LOG = fileLogger()

/**
 * Converts an [Icon] instance into a [IconRpc] which can be used in RPC calls and stored in Rhizome.
 */
@ApiStatus.Internal
fun Icon.toRpc(): IconRpc {
  val icon = this
  val serializedIcon = IconSerializer.EP_NAME.extensionList.firstNotNullOfOrNull { serializer ->
    try {
      serializer.serialize(icon)
    }
    catch (e: Exception) {
      LOG.error("Error during icon serialization", e)
      null
    }
  }
  return IconRpc(serializedIcon, icon)
}

/**
 * Retrieves the [Icon] associated with the given [IconRpc].
 */
@ApiStatus.Internal
fun IconRpc.icon(): Icon {
  if (localIcon != null) {
    return localIcon
  }
  val deserializedIcon = serializedValue?.let { serializedValue ->
    IconSerializer.EP_NAME.extensionList.firstNotNullOfOrNull { serializer ->
      try {
        serializer.deserialize(serializedValue)
      }
      catch (e: Exception) {
        LOG.error("Error during icon deserialization", e)
        null
      }
    }
  }

  return deserializedIcon ?: error("Cannot obtain icon from a remote model. Most probably plugin with serialization EP was disabled")
}

@ApiStatus.Internal
@Serializable
class IconRpc internal constructor(@Serializable internal val serializedValue: SerializedValue? = null, @Transient internal val localIcon: Icon? = null)

/**
 * Provides a way to serialize an [Icon] to [SerializedValue] which will be used to send [IconRpc] through Rpc or Rhizome.
 */
@ApiStatus.Internal
interface IconSerializer {
  fun serialize(icon: Icon): SerializedValue
  fun deserialize(value: SerializedValue): Icon

  @ApiStatus.Internal
  companion object {
    val EP_NAME: ExtensionPointName<IconSerializer> = ExtensionPointName<IconSerializer>("com.intellij.iconSerializer")
  }
}