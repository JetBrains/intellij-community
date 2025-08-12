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
 * Converts an [VirtualFile] instance into a [VirtualFileId] which can be used in RPC calls and stored in Rhizome.
 */
@ApiStatus.Experimental
fun VirtualFile.rpcId(): VirtualFileId {
  val file = this
  val serializedFile = serializeToRpc(file)
  return VirtualFileId(serializedFile, file)
}

/**
 * Retrieves the [VirtualFile] associated with the given [VirtualFileId].
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