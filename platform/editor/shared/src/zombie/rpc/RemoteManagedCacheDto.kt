// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editor.zombie.rpc

import com.intellij.util.io.DataExternalizer
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.io.*

@Serializable
@ApiStatus.Internal
class RemoteManagedCacheDto(
  val data: ByteArray,
) {
  fun <V> toValue(externalizer: DataExternalizer<V>): V = ByteArrayInputStream(data).use {
    DataInputStream(it).use { dataInput ->
      externalizer.read(dataInput)
    }
  }
  companion object {
    fun<V> V.fromValue(externalizer: DataExternalizer<V>): RemoteManagedCacheDto {
       return ByteArrayOutputStream().use {bao ->
         DataOutputStream(bao).use { dataOutput: DataOutput ->
           externalizer.save(dataOutput, this)
           RemoteManagedCacheDto(bao.toByteArray())
         }
      }
    }
  }
}