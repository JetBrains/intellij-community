// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.rpc

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.editor.Document
import fleet.util.openmap.SerializedValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

private val LOG = fileLogger()

/**
 * Converts an [Document] instance into a [DocumentId] which can be used in RPC calls and stored in Rhizome.
 */
@ApiStatus.Experimental
fun Document.rpcId(): DocumentId {
  val file = this
  val serializedFile = serializeToRpc(file)
  return DocumentId(serializedFile, file)
}

/**
 * Retrieves the [Document] associated with the given [DocumentId].
 */
@ApiStatus.Experimental
fun DocumentId.document(): Document? {
  if (localDocument != null) {
    return localDocument
  }

  return deserializeFromRpc<Document>(serializedValue) ?: run {
    LOG.debug("Cannot deserialize DocumentId: $this")
    null
  }
}

@ApiStatus.Experimental
@Serializable
class DocumentId internal constructor(@Serializable internal val serializedValue: SerializedValue? = null, @Transient internal val localDocument: Document? = null)