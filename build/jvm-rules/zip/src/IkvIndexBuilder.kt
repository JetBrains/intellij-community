// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

const val INDEX_FILENAME: String = "__index__"
internal val INDEX_FILENAME_BYTES = "__index__".toByteArray()

class IkvIndexBuilder(@JvmField val writeCrc32: Boolean = true) {
  private val entries = ObjectLinkedOpenHashSet<IkvIndexEntry>()

  @JvmField
  val names: MutableList<ByteArray> = mutableListOf()

  @JvmField
  val classPackages: LongOpenHashSet = LongOpenHashSet()

  @JvmField
  val resourcePackages: LongOpenHashSet = LongOpenHashSet()

  internal fun add(entry: IkvIndexEntry) {
    if (!entries.add(entry)) {
      throw IllegalStateException("$entry duplicates ${entries.find { it == entry }}\n")
    }
  }

  fun dataSize(): Int = (entries.size * Long.SIZE_BYTES * 2) + Int.SIZE_BYTES + 1

  fun write(buffer: ByteBuffer) {
    assert(buffer.order() == ByteOrder.LITTLE_ENDIAN)
    for (entry in entries) {
      buffer.putLong(entry.longKey)
      buffer.putLong(entry.offset shl 32 or (entry.size.toLong() and 0xffffffffL))
    }

    buffer.putInt(entries.size)
    // has size - redundant, cannot be removed to avoid format change
    buffer.put(1.toByte())
  }
}

class IkvIndexEntry(
  @JvmField internal val longKey: Long,
  @JvmField internal val offset: Long,
  @JvmField internal val size: Int,
) {
  override fun equals(other: Any?): Boolean = other is IkvIndexEntry && longKey == other.longKey

  override fun hashCode(): Int = longKey.toInt()
}

internal fun writeIndex(indexWriter: IkvIndexBuilder, indexDataSize: Int, stream: ZipArchiveOutputStream) {
  // write package class and resource hashes
  val classPackages = indexWriter.classPackages
  val resourcePackages = indexWriter.resourcePackages
  val packageIndexSize = (classPackages.size + resourcePackages.size) * Long.SIZE_BYTES
  val packageIndexSizeHeaderSize = Int.SIZE_BYTES * 2
  val nameSize = indexWriter.names.sumOf { it.size + Short.SIZE_BYTES }

  stream.writeDataWithKnownSize(
    path = INDEX_FILENAME_BYTES,
    size = indexDataSize + packageIndexSizeHeaderSize + packageIndexSize + nameSize,
    crc32 = if (indexWriter.writeCrc32) CRC32() else null,
  ) { buffer ->
    assert(buffer.order() == ByteOrder.LITTLE_ENDIAN)
    indexWriter.write(buffer)

    if (packageIndexSize == 0) {
      buffer.putLong(0)
    }
    else {
      val classPackageArray = indexWriter.classPackages.toLongArray()
      val resourcePackageArray = indexWriter.resourcePackages.toLongArray()

      // same content for same data
      classPackageArray.sort()
      resourcePackageArray.sort()

      buffer.putInt(classPackages.size)
      buffer.putInt(resourcePackages.size)
      buffer.asLongBuffer().apply {
        put(classPackageArray)
        put(resourcePackageArray)
        buffer.position(buffer.position() + packageIndexSize)
      }
    }

    // write names
    for (name in indexWriter.names) {
      buffer.putShort(name.size.toShort())
    }

    for (name in indexWriter.names) {
      buffer.put(name)
    }
  }
}
