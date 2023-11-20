// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog.AppendOnlyLogFactory
import com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator.DurableEnumeratorFactory
import com.intellij.util.io.DurableDataEnumerator
import com.intellij.util.io.IOUtil
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.PersistentEnumerator
import com.intellij.util.io.dev.appendonlylog.AppendOnlyLog
import com.intellij.util.io.dev.enumerator.KeyDescriptorEx
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataOutput
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.file.Path



class PersistentTimestampsEnumerator(path: Path) :
  PersistentEnumerator<TimestampsImmutable>(path, TimestampsKeyDescriptor(), 1024, null, 1) {
}

class DurableTimestampEnumerator(path: Path)
  : DurableDataEnumerator<TimestampsImmutable> by DurableEnumeratorFactory
  .defaultWithInMemoryMap(TimestampsKeyDescriptorEx())
  .valuesLogFactory(
    AppendOnlyLogFactory.withDefaults()
      .pageSize(256 * IOUtil.KiB) //smaller page size: we expect just (100s..1000s) records in the enumerator
  )
  .open(path) {

}

/** Descriptor for [PersistentEnumerator] */
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

/** Descriptor for [com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator.DurableEnumerator] */
class TimestampsKeyDescriptorEx : KeyDescriptorEx<TimestampsImmutable> {

  override fun areEqual(timestamp1: TimestampsImmutable, timestamp2: TimestampsImmutable): Boolean {
    return timestamp1 == timestamp2
  }

  override fun hashCodeOf(timestamp: TimestampsImmutable): Int {
    return timestamp.hashCode()
  }

  //RC: beware of binary format difference between this and TimestampsKeyDescriptor:
  //    here we don't prefix record with (int32) size, because append-only-log
  //    already keeps the record size internally

  override fun read(input: ByteBuffer): TimestampsImmutable {
    return TimestampsImmutable.readTimestamps(input)
  }

  override fun saveToLog(timestamps: TimestampsImmutable,
                         log: AppendOnlyLog): Long {
    val outStream = ByteArrayOutputStream(64)
    DataOutputStream(outStream).use { timestamps.writeToStream(it) }
    return log.append(outStream.toByteArray())
  }
}
