// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.util.indexing

import com.intellij.platform.util.io.storages.appendonlylog.AppendOnlyLogFactory
import com.intellij.platform.util.io.storages.DataExternalizerEx
import com.intellij.platform.util.io.storages.enumerator.DurableEnumeratorFactory
import com.intellij.platform.util.io.storages.KeyDescriptorEx
import com.intellij.util.SystemProperties
import com.intellij.util.io.*
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataOutput
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.file.Path


private val USE_MAPPED_ENUMERATOR = SystemProperties.getBooleanProperty("idea.use-mapped-index-stamps-enumerator", true)

fun createTimestampsEnumerator(path: Path): DurableDataEnumerator<TimestampsImmutable> {
  return if (!USE_MAPPED_ENUMERATOR) {
    PersistentEnumerator(path, TimestampsKeyDescriptor(), 1024, null, 1)
  }
  else {
    //make file name differ, so difference in binary formats won't lead to the issues
    val pathWithNewFileName: Path = path.parent.resolve(path.fileName.toString() + ".mmapped")
    val durableEnumerator = DurableEnumeratorFactory
      .defaultWithInMemoryMap(TimestampsKeyDescriptorEx())
      .valuesLogFactory(
        AppendOnlyLogFactory.withDefaults()
          .pageSize(256 * IOUtil.KiB) //use small page size: we expect only (100..1000)s records in the enumerator
      ).open(pathWithNewFileName)
    return object : DurableDataEnumerator<TimestampsImmutable> by durableEnumerator,
                    Unmappable by durableEnumerator {
      //TODO RC: general DataEnumerator contract states .valueOf(unknownId) == null.
      //         DurableEnumerator violates this contract and throws Exception for unknownId.
      //         This was done intentionally, because I believe supplying the unknownId to enumerator is almost always
      //         an error, and should be processed accordingly. But currently IndexingStamp code requires null to be
      //         returned, so the adapter below. I think we should re-consider IndexingStamp code that relies on
      //         null so that exception is caught instead -- e.g. because exception carries more information about
      //         what is going on
      override fun valueOf(idx: Int): TimestampsImmutable? {
        try {
          return durableEnumerator.valueOf(idx)
        }
        catch (e: Exception) {
          return null
        }
      }
    }
  }
}

/** Descriptor for [PersistentEnumerator] */
@ApiStatus.Internal
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
@ApiStatus.Internal
class TimestampsKeyDescriptorEx : KeyDescriptorEx<TimestampsImmutable> {

  override fun isEqual(timestamp1: TimestampsImmutable, timestamp2: TimestampsImmutable): Boolean {
    return timestamp1 == timestamp2
  }

  override fun getHashCode(timestamp: TimestampsImmutable): Int {
    return timestamp.hashCode()
  }

  //RC: beware of binary format difference between this and TimestampsKeyDescriptor:
  //    here we don't prefix record with (int32) size, because append-only-log
  //    already keeps the record size internally

  override fun read(input: ByteBuffer): TimestampsImmutable {
    return TimestampsImmutable.readTimestamps(input)
  }

  override fun writerFor(timestamps: TimestampsImmutable): DataExternalizerEx.KnownSizeRecordWriter {
    val outStream = ByteArrayOutputStream(64)
    DataOutputStream(outStream).use { timestamps.writeToStream(it) }
    return DataExternalizerEx.fromBytes(outStream.toByteArray())
  }
}
