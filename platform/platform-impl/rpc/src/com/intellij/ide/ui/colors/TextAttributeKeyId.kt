// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental
package com.intellij.ide.ui.colors

import com.intellij.ide.rpc.deserializeFromRpc
import com.intellij.ide.rpc.serializeToRpc
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import fleet.util.openmap.SerializedValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

private val LOG = fileLogger()

@ApiStatus.Experimental
fun TextAttributesKey.rpcId(): TextAttributeKeyId {
  val key = this
  val serializedAttributeKey = serializeToRpc(key)

  return TextAttributeKeyId(serializedAttributeKey, key)
}

@ApiStatus.Experimental
fun TextAttributeKeyId.key(): TextAttributesKey {
  if (localKey != null) {
    return localKey
  }

  return deserializeFromRpc<TextAttributesKey>(serializedValue) ?: run {
    LOG.debug("Cannot deserialize text attributes key from a remote model, default text ones are used instead.")
    HighlighterColors.TEXT
  }
}

@ApiStatus.Experimental
@Serializable
class TextAttributeKeyId internal constructor(
  @Serializable @JvmField internal val serializedValue: SerializedValue? = null,
  @Transient @JvmField internal val localKey: TextAttributesKey? = null,
)