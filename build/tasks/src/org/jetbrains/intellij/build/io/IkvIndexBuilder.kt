// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import io.netty.buffer.ByteBuf
import it.unimi.dsi.fastutil.longs.LongOpenHashSet

class IkvIndexBuilder() {
  private val entries = LinkedHashSet<IkvIndexEntry>()

  @JvmField
  val names = mutableListOf<ByteArray>()

  @JvmField
  val classPackages: LongOpenHashSet = LongOpenHashSet()

  @JvmField
  val resourcePackages: LongOpenHashSet = LongOpenHashSet()

  internal fun add(entry: IkvIndexEntry) {
    if (!entries.add(entry)) {
      throw IllegalStateException("$entry duplicates ${entries.find { it == entry }}\n")
    }
  }

  fun write(buffer: ByteBuf) {
    buffer.ensureWritable((entries.size * Long.SIZE_BYTES * 2) + Int.SIZE_BYTES + 1)
    for (entry in entries) {
      buffer.writeLongLE(entry.longKey)
      buffer.writeLongLE(entry.offset shl 32 or (entry.size.toLong() and 0xffffffffL))
    }

    buffer.writeIntLE(entries.size)
    // has size - redundant, cannot be removed to avoid format change
    buffer.writeByte(1)
  }
}

class IkvIndexEntry(@JvmField internal val longKey: Long, @JvmField internal val offset: Long, @JvmField internal val size: Int) {
  override fun equals(other: Any?): Boolean = longKey == (other as? IkvIndexEntry)?.longKey

  override fun hashCode(): Int = longKey.toInt()
}