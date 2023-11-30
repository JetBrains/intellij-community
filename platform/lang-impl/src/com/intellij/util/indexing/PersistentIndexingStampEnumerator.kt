// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.PersistentEnumerator
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataOutput
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.file.Path

class TimestampsKeyDescriptor : KeyDescriptor<TimestampsImmutable> {
  override fun isEqual(val1: TimestampsImmutable, val2: TimestampsImmutable): Boolean {
    return val1 == val2
  }

  override fun getHashCode(value: TimestampsImmutable): Int {
    return value.hashCode()
  }

  override fun save(out: DataOutput, value: TimestampsImmutable) {
    val outStream = ByteArrayOutputStream(64)
    DataOutputStream(outStream).use { value.writeToStream(it) }
    out.writeInt(outStream.size())
    out.write(outStream.toByteArray())
  }

  override fun read(dataIn: DataInput): TimestampsImmutable {
    val size = dataIn.readInt()
    val data = ByteArray(size)
    dataIn.readFully(data)
    return TimestampsImmutable.readTimestamps(ByteBuffer.wrap(data))
  }

}

class PersistentTimestampsEnumerator(path: Path) :
  PersistentEnumerator<TimestampsImmutable>(path, TimestampsKeyDescriptor(), 1024, null, 1) {
}