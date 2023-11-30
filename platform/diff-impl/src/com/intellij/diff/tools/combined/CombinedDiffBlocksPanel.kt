// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.ui.scale.JBUIScale
import java.awt.*
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
                                       private val lastBlockHeightLogic: (Int) -> Int) : JPanel(null) {
  private val blocksLayout = CombinedDiffBlocksLayout()
  private val holders: MutableMap<CombinedBlockId, Holder> = mutableMapOf()

  init {
    layout = blocksLayout

    blockOrder.iterateBlocks().forEach { it ->
      setPlaceholder(it)
    }
  }

  fun setContent(blockId: CombinedBlockId, component: JComponent) {
    val holder = holders[blockId]
    if (holder is RealComponent) {
      remove(holder.component)
    }
    holders[blockId] = RealComponent(component)
    add(component)
  }

  fun setPlaceholder(blockId: CombinedBlockId, height: Int? = null) {
    val holder = holders[blockId]
    holders[blockId] = Placeholder(height ?: CombinedDiffLoadingBlock.DEFAULT_LOADING_BLOCK_HEIGHT.get())
    holder ?: return
    if (holder is RealComponent) {
      remove(holder.component)
    }
  }

  fun getBoundsForBlock(blockId: CombinedBlockId): BlockBounds {
    var minY = 0
    var maxY = 0
    holders[blockId] ?: throw IllegalStateException()

    for ((index, id) in blockOrder.iterateBlocks().withIndex()) {
      val holder = getHolder(id)
      minY = maxY
      maxY = minY + calcHeight(index, holder)

      if (id == blockId) break
      maxY+= gap()
    }

    return BlockBounds(blockId, minY, maxY)
  }

  fun getBlockBounds(): List<BlockBounds> {
    var minY: Int
    var maxY = 0
    val bounds = mutableListOf<BlockBounds>()
    for ((index, id) in blockOrder.iterateBlocks().withIndex()) {
      val holder = getHolder(id)
      minY = maxY
      maxY = minY + calcHeight(index, holder)

      bounds.add(BlockBounds(id, minY, maxY))
      maxY+= gap()
    }
    return bounds
  }

  private fun calcHeight(index: Int, holder: Holder): Int {
    if (index == holders.size - 1) {
      return lastBlockHeightLogic(holder.height)
    }

    return holder.height
  }

  private fun getHolder(id: CombinedBlockId): Holder = holders[id]!!

  private inner class CombinedDiffBlocksLayout : LayoutManager2 {
    override fun preferredLayoutSize(parent: Container?): Dimension {
      parent ?: return Dimension(0, 0)

      val w = parent.width
      val h = holders.values.mapIndexed { index, holder -> calcHeight(index, holder) + gap() }.sum()

      return Dimension(w, h)
    }

    override fun layoutContainer(parent: Container?) {
      parent ?: return
      val x = left()
      var y = 0
      val w = parent.width - left() - right()

      blockOrder.iterateBlocks().forEachIndexed { index, it ->
        val holder = getHolder(it)
        val height = calcHeight(index, holder)
        if (holder is RealComponent) {
          holder.component.bounds = Rectangle(x, y, w, height)
        }

        y += height + gap()
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

private fun gap(): Int = JBUIScale.scale(CombinedDiffUI.GAP_BETWEEN_BLOCKS)
private fun left(): Int = JBUIScale.scale(CombinedDiffUI.LEFT_RIGHT_INSET)
private fun right(): Int =JBUIScale.scale(CombinedDiffUI.LEFT_RIGHT_INSET)

