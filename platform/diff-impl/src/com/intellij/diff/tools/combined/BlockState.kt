// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.tools.util.PrevNextDifferenceIterable

interface BlockOrder {
  fun iterateBlocks(): Iterable<CombinedBlockId>

  val blocksCount: Int
}

class BlockState(list: List<CombinedBlockId>, current: CombinedBlockId) : PrevNextDifferenceIterable, BlockOrder {
  private val blocks: List<CombinedBlockId> = list.toList()

  private val blockByIndex: MutableMap<CombinedBlockId, Int> = mutableMapOf()

  var currentBlock: CombinedBlockId = current

  init {
    blocks.forEachIndexed { index, block ->
      blockByIndex[block] = index
    }
    // todo: find and fix initial problem in Space review integration
    if (!blocks.contains(current)) {
      currentBlock = blocks.first()
    }
  }

  fun indexOf(blockId: CombinedBlockId): Int = blockByIndex[blockId]!!

  operator fun get(index: Int): CombinedBlockId? = if (index in blocks.indices) blocks[index] else null

  override val blocksCount: Int
    get() = blocks.size

  override fun iterateBlocks(): Iterable<CombinedBlockId> = blocks.asIterable()

  override fun canGoPrev(): Boolean = currentIndex > 0

  override fun canGoNext(): Boolean = currentIndex < blocksCount - 1

  override fun goPrev() {
    currentBlock = blocks[this.currentIndex - 1]
  }

  override fun goNext() {
    currentBlock = blocks[this.currentIndex + 1]
  }

  private val currentIndex: Int
    get() = indexOf(currentBlock)
}