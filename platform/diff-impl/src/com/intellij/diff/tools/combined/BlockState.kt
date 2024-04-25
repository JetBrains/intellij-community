// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.tools.util.PrevNextDifferenceIterable
import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher
import java.util.*
import kotlin.properties.Delegates

interface BlockOrder {
  fun iterateBlocks(): Iterable<CombinedBlockId>
  fun indexOf(blockId: CombinedBlockId): Int
  fun getOrNull(index: Int): CombinedBlockId?
  val blocksCount: Int
}

fun interface BlockStateListener : EventListener {
  fun onCurrentChanged(oldBlockId: CombinedBlockId, newBlockId: CombinedBlockId)
}

class BlockState(list: List<CombinedBlockId>, current: CombinedBlockId) : PrevNextDifferenceIterable, BlockOrder {

  private val eventDispatcher = EventDispatcher.create(BlockStateListener::class.java)

  private val blocks: List<CombinedBlockId> = list.toList()

  private val blockByIndex: MutableMap<CombinedBlockId, Int> = mutableMapOf()

  var currentBlock: CombinedBlockId by Delegates.observable(current) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      eventDispatcher.multicaster.onCurrentChanged(oldValue, newValue)
    }
  }

  fun addListener(listener: BlockStateListener, disposable: Disposable) {
    eventDispatcher.addListener(listener, disposable)
  }

  init {
    blocks.forEachIndexed { index, block ->
      blockByIndex[block] = index
    }
    // todo: find and fix initial problem in Space review integration
    if (!blocks.contains(current)) {
      currentBlock = blocks.first()
    }
  }

  override fun indexOf(blockId: CombinedBlockId): Int = blockByIndex[blockId]!!

  override fun getOrNull(index: Int): CombinedBlockId? = if (index in blocks.indices) blocks[index] else null

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
