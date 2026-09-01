// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.hprof.util

import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * One file-backed record per object id with the traverse outputs interleaved.
 *
 * A record holds 12 bytes: the parent id, the subtree size, and a packed field.
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
class FileBackedObjectMetaList private constructor(private val buffer: ByteBuffer) {

  val parentList: IntList = object : IntList {
    override fun get(index: Int): Int = buffer.getInt(index * RECORD_BYTES)
    override fun set(index: Int, value: Int) {
      buffer.putInt(index * RECORD_BYTES, value)
    }
  }

  val sizesList: IntList = object : IntList {
    override fun get(index: Int): Int = buffer.getInt(index * RECORD_BYTES + SIZE_OFFSET)
    override fun set(index: Int, value: Int) {
      buffer.putInt(index * RECORD_BYTES + SIZE_OFFSET, value)
    }
  }

  val refIndexList: UByteList = object : UByteList {
    override fun get(index: Int): Int = buffer.getInt(index * RECORD_BYTES + PACKED_OFFSET) and 0xFF
    override fun set(index: Int, value: Int) {
      assert(value in 0..255)
      val position = index * RECORD_BYTES + PACKED_OFFSET
      buffer.putInt(position, (buffer.getInt(position) and 0xFF.inv()) or value)
    }
  }

  val classIndexList: IntList = object : IntList {
    override fun get(index: Int): Int = buffer.getInt(index * RECORD_BYTES + PACKED_OFFSET) ushr 8
    override fun set(index: Int, value: Int) {
      // A class index above the packed capacity stays zero, and readers fall back to the navigator.
      val recorded = if (value in 0..MAX_CLASS_INDEX) value else 0
      val position = index * RECORD_BYTES + PACKED_OFFSET
      buffer.putInt(position, (recorded shl 8) or (buffer.getInt(position) and 0xFF))
    }
  }

  companion object {
    private const val RECORD_BYTES = 12
    private const val SIZE_OFFSET = 4
    private const val PACKED_OFFSET = 8
    private const val MAX_CLASS_INDEX = 0xFFFFFF

    fun isSupported(size: Long): Boolean = size * RECORD_BYTES <= Int.MAX_VALUE

    fun createEmpty(channel: FileChannel, size: Long): FileBackedObjectMetaList {
      FileBackedHashMap.createEmptyFile(channel, size * RECORD_BYTES)
      return FileBackedObjectMetaList(channel.map(FileChannel.MapMode.READ_WRITE, 0, channel.size()))
    }
  }
}
