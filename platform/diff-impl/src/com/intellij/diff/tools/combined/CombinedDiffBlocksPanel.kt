// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.ui.scale.JBUIScale
import java.awt.*
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel

data class BlockBounds(val blockId: CombinedBlockId, val minY: Int, val maxY: Int) {
  val height: Int
    get() = maxY - minY
}

private sealed class Holder {
  abstract val height: Int
}

private class Placeholder(override val height: Int) : Holder()
private class RealComponent(val component: Component) : Holder() {
  override val height: Int get() = component.preferredSize.height
}

internal class CombinedDiffBlocksPanel(private val blockOrder: BlockOrder,
                                       private val lastBlockHeightLogic: (Pair<CombinedBlockId, Int>) -> Int) : JPanel(null) {
  private val blocksLayout = CombinedDiffBlocksLayout()
  private val holders: MutableList<Holder?> = mutableListOf()

  init {
    layout = blocksLayout

    holders.addAll(Collections.nCopies(blockOrder.blocksCount, null))
  }

  fun setContent(blockId: CombinedBlockId, component: JComponent) {
    val blockIndex = blockOrder.indexOf(blockId)

    val newHolder = RealComponent(component)
    val oldHolder = holders.set(blockIndex, newHolder)

    if (oldHolder is RealComponent) {
      remove(oldHolder.component)
    }
    add(component)
  }

  fun setPlaceholder(blockId: CombinedBlockId, height: Int? = null) {
    val blockIndex = blockOrder.indexOf(blockId)
    setPlaceholder(blockIndex, height)
  }

  private fun setPlaceholder(blockIndex: Int, height: Int? = null) {
    val oldHolder = if (height != null) {
      holders.set(blockIndex, Placeholder(height))
    }
    else {
      holders.set(blockIndex, null)
    }

    if (oldHolder is RealComponent) {
      remove(oldHolder.component)
    }
  }

  fun getHeightForBlock(blockId: CombinedBlockId): Int {
    val defaultPlaceholderHeight = defaultPlaceholderBlockHeight()

    val blockIndex = blockOrder.indexOf(blockId)
    val holder = holders[blockIndex]
    return calcHeight(blockIndex, holder, defaultPlaceholderHeight)
  }

  fun getBoundsForBlock(blockId: CombinedBlockId): BlockBounds {
    val gap = gap()
    val defaultPlaceholderHeight = defaultPlaceholderBlockHeight()

    val blockIndex = blockOrder.indexOf(blockId)

    var minY = 0
    var maxY = 0

    for ((index, _) in blockOrder.iterateBlocks().withIndex()) {
      val holder = holders[index]
      minY = maxY
      maxY = minY + calcHeight(index, holder, defaultPlaceholderHeight)

      if (index == blockIndex) break
      maxY += gap
    }

    return BlockBounds(blockId, minY, maxY)
  }

  fun getBlockBounds(): List<BlockBounds> {
    val gap = gap()
    val defaultPlaceholderHeight = defaultPlaceholderBlockHeight()

    var minY: Int
    var maxY = 0
    val bounds = mutableListOf<BlockBounds>()
    for ((index, id) in blockOrder.iterateBlocks().withIndex()) {
      val holder = holders[index]
      minY = maxY
      maxY = minY + calcHeight(index, holder, defaultPlaceholderHeight)

      bounds.add(BlockBounds(id, minY, maxY))
      maxY += gap
    }

    return bounds
  }

  private fun calcHeight(index: Int, holder: Holder?, defaultPlaceholderHeight: Int): Int {
    val height = holder?.height ?: defaultPlaceholderHeight
    val blockId = blockOrder.getOrNull(index)
    if (index == holders.size - 1 && blockId != null) {
      return lastBlockHeightLogic(blockId to height)
    }
    else {
      return height
    }
  }

  private inner class CombinedDiffBlocksLayout : LayoutManager2 {
    override fun preferredLayoutSize(parent: Container?): Dimension {
      parent ?: return Dimension(0, 0)

      val gap = gap()
      val defaultPlaceholderHeight = defaultPlaceholderBlockHeight()

      val w = parent.width

      var sumH = 0
      for ((index, _) in blockOrder.iterateBlocks().withIndex()) {
        val holder = holders[index]
        sumH += calcHeight(index, holder, defaultPlaceholderHeight) + gap
      }

      return Dimension(w, sumH)
    }

    override fun layoutContainer(parent: Container?) {
      parent ?: return

      val gap = gap()
      val defaultPlaceholderHeight = defaultPlaceholderBlockHeight()

      val x = left()
      var y = 0
      val w = parent.width - left() - right()

      for ((index, _) in blockOrder.iterateBlocks().withIndex()) {
        val holder = holders[index]
        val height = calcHeight(index, holder, defaultPlaceholderHeight)
        if (holder is RealComponent) {
          holder.component.bounds = Rectangle(x, y, w, height)
        }

        y += height + gap
      }
    }


    override fun minimumLayoutSize(parent: Container?): Dimension = preferredLayoutSize(parent)
    override fun maximumLayoutSize(target: Container?): Dimension = preferredLayoutSize(target)

    override fun getLayoutAlignmentX(target: Container?): Float = Component.TOP_ALIGNMENT
    override fun getLayoutAlignmentY(target: Container?): Float = Component.TOP_ALIGNMENT

    override fun addLayoutComponent(comp: Component?, constraints: Any?) {}
    override fun addLayoutComponent(name: String?, comp: Component?) {}
    override fun removeLayoutComponent(comp: Component?) {}
    override fun invalidateLayout(target: Container?) {}
  }
}

private fun defaultPlaceholderBlockHeight() = CombinedDiffLoadingBlock.DEFAULT_LOADING_BLOCK_HEIGHT.get()
private fun gap(): Int = JBUIScale.scale(CombinedDiffUI.GAP_BETWEEN_BLOCKS)
private fun left(): Int = JBUIScale.scale(CombinedDiffUI.LEFT_RIGHT_INSET)
private fun right(): Int = JBUIScale.scale(CombinedDiffUI.LEFT_RIGHT_INSET)

