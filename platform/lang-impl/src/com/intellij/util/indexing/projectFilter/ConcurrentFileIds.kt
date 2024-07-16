// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.projectFilter

import com.intellij.util.containers.ConcurrentBitSet
import com.intellij.util.io.DataOutputStream
import java.io.DataInputStream

class ConcurrentFileIds(private val fileIds: ConcurrentBitSet = ConcurrentBitSet.create()) {
  val cardinality: Int get() = fileIds.cardinality()
  val size: Int get() = fileIds.size()
  val empty: Boolean get() = fileIds.cardinality() == 0

  operator fun get(fileId: Int): Boolean = fileIds.get(fileId)
  operator fun set(fileId: Int, v: Boolean) = fileIds.set(fileId, v)
  fun clear() = fileIds.clear()
  fun writeTo(outputStream: DataOutputStream) {
    val words = fileIds.toIntArray()
    for (word in words) {
      outputStream.writeInt(word)
    }
  }

  companion object {
    fun readFrom(stream: DataInputStream) = ConcurrentFileIds(ConcurrentBitSet.readFrom(stream))
  }
}
