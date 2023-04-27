// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.util.containers.map2Array
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.event.AdjustmentListener
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.border.Border

@ApiStatus.Experimental
class ScrollableContentBorder private constructor(
  @SideMask sideMask: Int,
  private val color: Color = JBColor.border(),
  thickness: Int = 1
) : SideBorder(color, sideMask, thickness) {

  init {
    require(isOneSideBorder(this)) { "Only one-side borders are suitable" }
  }

  fun setVisible(isVisible: Boolean) {
    val color = if (isVisible) color else UIUtil.TRANSPARENT_COLOR
    lineColor = color
  }

  fun isVisible(): Boolean = lineColor != UIUtil.TRANSPARENT_COLOR

  companion object {
    @JvmStatic
    @JvmOverloads
    fun setup(scrollPane: JScrollPane,
              side: Side,
              targetComponent: JComponent = scrollPane) {
      setup(scrollPane, setOf(side), targetComponent)
    }

    @JvmStatic
    @JvmOverloads
    fun setup(scrollPane: JScrollPane,
              sides: Set<Side>,
              targetComponent: JComponent = scrollPane) {

      val borders = sides.map2Array { side ->
        val border = ScrollableContentBorder(side.toMask())
        when (side) {
          Side.TOP -> createTopOrLeftListener(targetComponent, border).also {
            scrollPane.verticalScrollBar?.addAdjustmentListener(it)
          }
          Side.BOTTOM -> createBottomOrRightListener(targetComponent, border).also {
            scrollPane.verticalScrollBar?.addAdjustmentListener(it)
          }
          Side.LEFT -> createTopOrLeftListener(targetComponent, border).also {
            scrollPane.horizontalScrollBar?.addAdjustmentListener(it)
          }
          Side.RIGHT -> createBottomOrRightListener(targetComponent, border).also {
            scrollPane.horizontalScrollBar?.addAdjustmentListener(it)
          }
        }
        border
      }


      targetComponent.border = if (borders.size == 1) borders[0] else JBUI.Borders.compound(*borders)
    }

    private fun isOneSideBorder(sideBorder: Border): Boolean {
      if (sideBorder !is SideBorder) return false

      return with(sideBorder.getBorderInsets(null)) {
        sequenceOf(top, bottom, right, left).filter { it != 0 }.count() == 1
      }
    }
  }
}

private fun createTopOrLeftListener(targetComponent: JComponent,
                                    border: ScrollableContentBorder): AdjustmentListener = AdjustmentListener {
  val visible = border.isVisible()
  border.setVisible(it.adjustable.value != 0 || !ExperimentalUI.isNewUI())
  if (visible != border.isVisible()) {
    targetComponent.repaint()
  }
}

private fun createBottomOrRightListener(targetComponent: JComponent,
                                        border: ScrollableContentBorder): AdjustmentListener = AdjustmentListener {
  val visible = border.isVisible()
  with(it.adjustable) {
    border.setVisible(value != maximum - visibleAmount || !ExperimentalUI.isNewUI())
  }
  if (visible != border.isVisible()) {
    targetComponent.repaint()
  }
}

enum class Side {
  TOP,
  BOTTOM,
  LEFT,
  RIGHT;

  companion object {
    val TOP_AND_BOTTOM: Set<Side>
      get() = setOf(TOP, BOTTOM)
  }
}

private fun Side.toMask(): Int = when (this) {
  Side.TOP -> SideBorder.TOP
  Side.BOTTOM -> SideBorder.BOTTOM
  Side.LEFT -> SideBorder.LEFT
  Side.RIGHT -> SideBorder.RIGHT
}
