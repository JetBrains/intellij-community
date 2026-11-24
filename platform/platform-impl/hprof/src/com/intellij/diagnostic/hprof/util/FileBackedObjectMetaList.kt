// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.hprof.util

import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * One file-backed record per object id with the traverse outputs interleaved.
 *
 * A record holds 12 bytes: the parent id, the subtree size, and a packed field.
 * The subtree size is an unsigned count of 4-byte units.
 * The packed field holds the class index in the high 24 bits and the reference
 * index in the low 8 bits.
 *
 * The GC path walk of [com.intellij.diagnostic.hprof.analysis.GCRootPathsTree]
 * reads all fields of one object per step. One record keeps these reads on one
 * page, where separate lists touch up to four pages per step.
 *
 * The views are not thread-safe. The set operations of [refIndexList] and
 * [classIndexList] read and rewrite the shared packed field.
 */
@ApiStatus.Internal
class FileBackedObjectMetaList private constructor(
  private val buffers: Array<ByteBuffer>,
  private val recordsPerBufferShift: Int
) {
  private val recordsPerBufferMask = (1 shl recordsPerBufferShift) - 1

  val parentList: IntList = object : IntList {
    override fun get(index: Int): Int = recordBuffer(index).getInt(recordOffset(index))
    override fun set(index: Int, value: Int) {
      recordBuffer(index).putInt(recordOffset(index), value)
    }
  }

  val sizesList: IntList = object : IntList {
    override fun get(index: Int): Int = recordBuffer(index).getInt(recordOffset(index) + SIZE_OFFSET)
    override fun set(index: Int, value: Int) {
      recordBuffer(index).putInt(recordOffset(index) + SIZE_OFFSET, value)
    }
  }

  val refIndexList: UByteList = object : UByteList {
    override fun get(index: Int): Int = recordBuffer(index).getInt(recordOffset(index) + PACKED_OFFSET) and 0xFF
    override fun set(index: Int, value: Int) {
      assert(value in 0..255)
      val records = recordBuffer(index)
      val position = recordOffset(index) + PACKED_OFFSET
      records.putInt(position, (records.getInt(position) and 0xFF.inv()) or value)
    }
  }

  val classIndexList: IntList = object : IntList {
    override fun get(index: Int): Int = recordBuffer(index).getInt(recordOffset(index) + PACKED_OFFSET) ushr 8
    override fun set(index: Int, value: Int) {
      // A class index above the packed capacity stays zero, and readers fall back to the navigator.
      val recorded = if (value in 0..MAX_CLASS_INDEX) value else 0
      val records = recordBuffer(index)
      val position = recordOffset(index) + PACKED_OFFSET
      records.putInt(position, (recorded shl 8) or (records.getInt(position) and 0xFF))
    }
  }

  private fun recordBuffer(index: Int): ByteBuffer = buffers[index ushr recordsPerBufferShift]

  private fun recordOffset(index: Int): Int = (index and recordsPerBufferMask) * RECORD_BYTES

  companion object {
    private const val RECORD_BYTES = 12
    private const val SIZE_OFFSET = 4
    private const val PACKED_OFFSET = 8
    private const val MAX_CLASS_INDEX = 0xFFFFFF

    // One buffer holds 2^26 records, so a record never crosses a buffer boundary.
    private const val RECORDS_PER_BUFFER_SHIFT = 26

    fun getFileSize(size: Long): Long = size * RECORD_BYTES

    fun createEmpty(channel: FileChannel, size: Long): FileBackedObjectMetaList {
      return createEmpty(channel, size, RECORDS_PER_BUFFER_SHIFT)
    }

    /**
     * Creates the list with 2^[recordsPerBufferShift] records in each buffer.
     * Tests use a small shift to put the buffer boundaries into a small file.
     */
    internal fun createEmpty(channel: FileChannel, size: Long, recordsPerBufferShift: Int): FileBackedObjectMetaList {
      FileBackedHashMap.createEmptyFile(channel, getFileSize(size))
      val bufferSize = (1L shl recordsPerBufferShift) * RECORD_BYTES
      val buffers = FileBackedHashMap.mapBuffers(channel, FileChannel.MapMode.READ_WRITE, bufferSize, bufferSize)
      return FileBackedObjectMetaList(buffers, recordsPerBufferShift)
    }
  }
}
