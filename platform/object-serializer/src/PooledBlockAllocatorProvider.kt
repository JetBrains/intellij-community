// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.amazon.ion.impl.bin.Block
import com.amazon.ion.impl.bin.BlockAllocator
import com.amazon.ion.impl.bin.BlockAllocatorProvider
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.TestOnly
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

internal class PooledBlockAllocatorProvider : BlockAllocatorProvider() {
  companion object {
    // 512 KB
    internal const val POOL_THRESHOLD = 512 * 1024
  }

  @Suppress("RemoveExplicitTypeArguments")
  private val allocators = ContainerUtil.createConcurrentIntObjectMap<PooledBlockAllocator>()

  private inner class PooledBlockAllocator(private val blockSize: Int) : BlockAllocator() {
    private val freeBlocks = ArrayList<Block>()

    private val blockCounter = AtomicInteger()

    val byteSize: Int
      get() = blockCounter.get() * blockSize

    override fun allocateBlock(): Block {
      val lastIndex = freeBlocks.lastIndex
      if (lastIndex != -1) {
        return freeBlocks.removeAt(lastIndex)
      }

      blockCounter.incrementAndGet()
      return object : Block(ByteArray(blockSize)) {
        override fun close() {
          reset()
          freeBlocks.add(this)
        }
      }
    }

    override fun getBlockSize() = blockSize

    override fun close() {
      if ((blockSize * freeBlocks.size) > POOL_THRESHOLD) {
        return
      }

      if (allocators.putIfAbsent(blockSize, this) == null) {
        removeExcess()
      }
      else {
        // help GC - nullize
        freeBlocks.clear()
        blockCounter.set(0)
      }
    }
  }

  @get:TestOnly
  val byteSize: Int
    get() {
      var totalByteSize = 0
      for (allocator in allocators.elements()) {
        totalByteSize += allocator.byteSize
      }
      return totalByteSize
    }

  override fun vendAllocator(blockSize: Int): BlockAllocator {
    if (blockSize <= 0) {
      throw IllegalArgumentException("Invalid block size: $blockSize")
    }

    // PooledBlockAllocator is not thread safe - do not put a new one to pool
    val result = allocators.remove(blockSize) ?: PooledBlockAllocator(blockSize)

    removeExcess()

    return result
  }

  private fun removeExcess() {
    var totalByteSize = 0
    val iterator = allocators.values().iterator()
    var isExcess = false
    while (iterator.hasNext()) {
      val allocator = iterator.next()
      if (isExcess) {
        iterator.remove()
        continue
      }

      totalByteSize += allocator.byteSize
      if (totalByteSize > POOL_THRESHOLD) {
        iterator.remove()
        isExcess = true
      }
    }
  }
}