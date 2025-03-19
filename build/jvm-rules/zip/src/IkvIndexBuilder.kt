// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import io.netty.buffer.ByteBuf
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet

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

  fun write(buffer: ByteBuf) {
    for (entry in entries) {
      buffer.writeLongLE(entry.longKey)
      buffer.writeLongLE(entry.offset shl 32 or (entry.size.toLong() and 0xffffffffL))
    }

    buffer.writeIntLE(entries.size)
    // has size - redundant, cannot be removed to avoid format change
    buffer.writeByte(1)
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