// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental
package com.intellij.ide.rpc

import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.diagnostic.fileLogger
import fleet.util.openmap.SerializedValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

private val LOG = fileLogger()

@ApiStatus.Experimental
fun Shortcut.rpcId(): ShortcutId {
  val serializedShortcut = serializeToRpc(this)

  return ShortcutId(serializedShortcut, this)
}

@ApiStatus.Experimental
fun ShortcutId.shortcut(): Shortcut? {
  if (localKey != null) {
    return localKey
  }

  return deserializeFromRpc<Shortcut>(serializedValue) ?: run {
    LOG.debug("Cannot deserialize shortcut from a remote model.")
    null
  }
}

@ApiStatus.Experimental
@Serializable
class ShortcutId internal constructor(
  @Serializable @JvmField internal val serializedValue: SerializedValue? = null,
  @Transient @JvmField internal val localKey: Shortcut? = null,
)