// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.colors

import com.intellij.ide.rpc.deserializeFromRpc
import com.intellij.ide.rpc.serializeToRpc
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.ui.JBColor
import fleet.util.openmap.SerializedValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

private val LOG = fileLogger()

/**
 * Converts an [Color] instance into a [ColorId] which can be used in RPC calls and stored in Rhizome.
 */
@ApiStatus.Experimental
fun Color.rpcId(): ColorId {
  val color = this
  val serializedColor = serializeToRpc(color)

  return ColorId(serializedColor, color)
}

/**
 * Retrieves the [Color] associated with the given [ColorId].
 */
@ApiStatus.Experimental
fun ColorId.color(): Color {
  if (localColor != null) {
    return localColor
  }

  return deserializeFromRpc<Color>(serializedValue) ?: run {
    LOG.debug("Cannot deserialize color from a remote model, black color is used instead.")
    JBColor.BLACK
  }
}

@ApiStatus.Experimental
@Serializable
class ColorId internal constructor(
  @Serializable @JvmField internal val serializedValue: SerializedValue? = null,
  @Transient @JvmField internal val localColor: Color? = null,
)