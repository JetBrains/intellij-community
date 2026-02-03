// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.vfs

import com.intellij.ide.rpc.deserializeFromRpc
import com.intellij.ide.rpc.serializeToRpc
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.vfs.VirtualFile
import fleet.util.openmap.SerializedValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

private val LOG = fileLogger()

/**
 * Converts a [VirtualFile] instance into a [VirtualFileId] which can be used in RPC calls and stored in Rhizome.
 *
 * **WARNING: This API is experimental and should be used with care.**
 *
 * In the monolith version of the IDE, this essentially stores a reference to the original VirtualFile object.
 * In Remote Development scenarios, the VirtualFile is serialized for transmission to the frontend.
 *
 * Important limitations:
 * - **Won't work for files created manually on the frontend** - only files that exist in the backend
 *   IDE instance can be properly serialized and retrieved
 * - VirtualFile references may become stale or invalid across RPC boundaries
 * - File system state may change between serialization and deserialization
 * - Some VirtualFile implementations may not serialize properly
 *
 * @return A [VirtualFileId] that can be used in RPC calls
 */
@ApiStatus.Experimental
fun VirtualFile.rpcId(): VirtualFileId {
  val file = this
  val serializedFile = serializeToRpc(file)
  return VirtualFileId(serializedFile, file)
}

/**
 * Retrieves the [VirtualFile] associated with the given [VirtualFileId].
 *
 * **WARNING: This API is experimental and should be used with care.**
 *
 * In the monolith version of the IDE, this method essentially does nothing - it just reuses the original
 * VirtualFile object that was passed to [rpcId]. However, in distributed scenarios (Remote Development), this
 * function attempts to deserialize a VirtualFile from RPC data.
 *
 * Important limitations:
 * - **Won't work for files created manually on the frontend** - only files that originated from
 *   the backend IDE instance can be properly retrieved
 * - VirtualFile references may become stale or invalid after RPC transmission
 * - File system state may have changed since serialization
 * - The file may have been deleted, moved, or modified
 * - Some VirtualFile implementations may not deserialize properly
 * - May return null if deserialization fails or the file is no longer available
 *
 * @return The [VirtualFile] if available, or null if deserialization fails or the file cannot be found
 */
@ApiStatus.Experimental
fun VirtualFileId.virtualFile(): VirtualFile? {
  if (localVirtualFile != null) {
    return localVirtualFile
  }

  return deserializeFromRpc<VirtualFile>(serializedValue) ?: run {
    LOG.debug("Cannot deserialize VirtualFileRpc: $this")
    null
  }
}

@ApiStatus.Experimental
@ConsistentCopyVisibility
@Serializable
data class VirtualFileId internal constructor(@Serializable internal val serializedValue: SerializedValue? = null, @Transient internal val localVirtualFile: VirtualFile? = null)