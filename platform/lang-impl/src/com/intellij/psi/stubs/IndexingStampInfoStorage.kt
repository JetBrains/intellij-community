// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.openapi.vfs.newvfs.persistent.dev.FastFileAttributes
import com.intellij.util.BitUtil
import com.intellij.util.indexing.impl.perFileVersion.AutoRefreshingOnVfsCloseRef
import com.intellij.util.indexing.impl.perFileVersion.IntFileAttribute
import com.intellij.util.io.DataInputOutputUtil
import java.io.IOException

internal sealed interface IndexingStampInfoStorage {
  companion object {
    @JvmStatic
    fun create(id: String, version: Int): IndexingStampInfoStorage {
      val fast = IntFileAttribute.shouldUseFastAttributes()
      val attribute = FileAttribute(id, version, true)
      return if (fast) IndexingStampInfoStorageOverFastAttributes(attribute) else IndexingStampStorageOverRegularAttributes(attribute)
    }
  }

  fun readStampInfo(fileId: Int): IndexingStampInfo?
  fun writeStampInfo(fileId: Int, stampInfo: IndexingStampInfo)
}


internal class IndexingStampInfoStorageOverFastAttributes(private val attribute: FileAttribute) : IndexingStampInfoStorage {
  private val attributeAccessor = AutoRefreshingOnVfsCloseRef{ fsRecordsImpl->
    FastFileAttributes.int4FileAttributes(
      fsRecordsImpl,
      attribute.id,
      attribute.version
    )
  }

  override fun readStampInfo(fileId: Int): IndexingStampInfo? {
    val int3 = IntArray(3)
    val attributeAccessor = attributeAccessor()
    int3[0] = attributeAccessor.readField(fileId, 0)
    int3[1] = attributeAccessor.readField(fileId, 1)
    int3[2] = attributeAccessor.readField(fileId, 2)
    return if (int3[0] == 0 && int3[1] == 0 && int3[2] == 0) {
      null
    }
    else {
      IndexingStampInfo.fromInt3(int3)
    }
  }

  override fun writeStampInfo(fileId: Int, stampInfo: IndexingStampInfo) {
    val int3 = stampInfo.toInt3()
    val attributeAccessor = attributeAccessor()
    attributeAccessor.write(fileId, 0, int3[0])
    attributeAccessor.write(fileId, 1, int3[1])
    attributeAccessor.write(fileId, 2, int3[2])
  }
}

internal class IndexingStampStorageOverRegularAttributes(private val attribute: FileAttribute) : IndexingStampInfoStorage {
  companion object {
    private const val IS_BINARY_MASK: Byte = 1
    private const val BYTE_AND_CHAR_LENGTHS_ARE_THE_SAME_MASK = (1 shl 1).toByte()
  }

  override fun readStampInfo(fileId: Int): IndexingStampInfo? {
    try {
      FSRecords.readAttributeWithLock(fileId, attribute).use { stream ->
        if (stream == null || stream.available() <= 0) {
          return null
        }
        val stamp = DataInputOutputUtil.readTIME(stream)
        val byteLength = DataInputOutputUtil.readLONG(stream)

        val flags = stream.readByte()
        val isBinary = BitUtil.isSet(flags, IS_BINARY_MASK)
        val readOnlyOneLength = BitUtil.isSet(flags, BYTE_AND_CHAR_LENGTHS_ARE_THE_SAME_MASK)
        val charLength = if (isBinary) {
          -1
        }
        else if (readOnlyOneLength) {
          byteLength.toInt()
        }
        else {
          DataInputOutputUtil.readINT(stream)
        }
        return IndexingStampInfo(stamp, byteLength, charLength, isBinary)
      }
    }
    catch (e: IOException) {
      StubUpdatingIndex.LOG.error(e)
      return null
    }
  }

  override fun writeStampInfo(fileId: Int, stampInfo: IndexingStampInfo) {
    try {
      FSRecords.writeAttribute(fileId, attribute).use { stream ->
        DataInputOutputUtil.writeTIME(stream, stampInfo.indexingFileStamp)
        DataInputOutputUtil.writeLONG(stream, stampInfo.indexingByteLength)

        val lengthsAreTheSame = stampInfo.indexingCharLength.toLong() == stampInfo.indexingByteLength
        var flags: Byte = 0
        flags = BitUtil.set(flags, IS_BINARY_MASK, stampInfo.isBinary)
        flags = BitUtil.set(flags, BYTE_AND_CHAR_LENGTHS_ARE_THE_SAME_MASK, lengthsAreTheSame)
        stream.writeByte(flags.toInt())
        if (!lengthsAreTheSame && !stampInfo.isBinary) {
          DataInputOutputUtil.writeINT(stream, stampInfo.indexingCharLength)
        }
      }
    }
    catch (e: IOException) {
      StubUpdatingIndex.LOG.error(e)
    }
  }
}