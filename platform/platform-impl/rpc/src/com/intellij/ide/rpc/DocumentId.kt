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
 * Converts a [Document] instance into a [DocumentId] which can be used in RPC calls and stored in Rhizome.
 *
 * **WARNING: This API is experimental and should be used with care.**
 *
 * In the monolith version of the IDE, this essentially stores a reference to the original document object.
 * In Remote Development scenarios, the document is serialized for transmission to the frontend.
 *
 * Important limitations:
 * - **Won't work for documents created manually on the frontend** - only documents that exist in the backend
 *   IDE instance can be properly serialized and retrieved
 * - Document state may not be fully synchronized with the backend's one
 *
 * @return A [DocumentId] that can be used in RPC calls
 */
@ApiStatus.Experimental
fun Document.rpcId(): DocumentId {
  val file = this
  val serializedFile = serializeToRpc(file)
  return DocumentId(serializedFile, file)
}

/**
 * Retrieves the [Document] associated with the given [DocumentId].
 *
 * **WARNING: This API is experimental and should be used with care.**
 *
 * In the monolith version of the IDE, this method essentially does nothing - it just reuses the original
 * document object that was passed to [rpcId]. However, in distributed scenarios (Remote Development), this
 * function attempts to deserialize a Document from RPC data.
 *
 * Important limitations:
 * - **Won't work for documents created manually on the frontend** - only documents that originated from
 *   the backend IDE instance can be properly retrieved
 * - Document state may not be fully synchronized with the frontend's one
 * - May return null if deserialization fails or the document is no longer available
 *
 * @return The [Document] if available, or null if deserialization fails or the document cannot be found
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