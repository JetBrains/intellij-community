// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serialization

import com.amazon.ion.impl.bin.Block
import com.amazon.ion.impl.bin.BlockAllocator
import com.amazon.ion.impl.bin.BlockAllocatorProvider
import org.jetbrains.annotations.TestOnly
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

internal class PooledBlockAllocatorProvider : BlockAllocatorProvider() {
  companion object {
    // 512 KB
    internal const val POOL_THRESHOLD = 512 * 1024
  }

  private val allocators = ConcurrentHashMap<Int, Queue<PooledBlockAllocator>>()

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
        // help GC - nullize
        freeBlocks.clear()
        blockCounter.set(0)
        return
      }

      allocators.getOrPut(blockSize, ::ConcurrentLinkedQueue).add(this)
      removeExcess()
    }
  }

  @get:TestOnly
  val byteSize: Int
    get() {
      var totalByteSize = 0
      for (allocator in allocators.values.flatten()) {
        totalByteSize += allocator.byteSize
      }
      return totalByteSize
    }

  override fun vendAllocator(blockSize: Int): BlockAllocator {
    if (blockSize <= 0) {
      throw IllegalArgumentException("Invalid block size: $blockSize")
    }

    val result = allocators[blockSize]?.poll() ?: PooledBlockAllocator(blockSize)
    removeExcess()

    return result
  }

  private fun removeExcess() {
    var totalByteSize = 0
    val queuesIterator = allocators.values.iterator()
    var isExcess = false
    while (queuesIterator.hasNext()) {
      val nextQueue = queuesIterator.next()
      val iterator = nextQueue.iterator()
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
}