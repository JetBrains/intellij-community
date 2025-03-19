// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.util.Key
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.lang.ref.WeakReference
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
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

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val alreadyHasBorder = when (mySideMask) {
      TOP -> ClientProperty.isTrue(c, HEADER_WITH_BORDER_ABOVE) || ClientProperty.isTrue(c, TOOLBAR_WITH_BORDER_ABOVE)
      LEFT -> ClientProperty.isTrue(c, TOOLBAR_WITH_BORDER_LEFT) || ClientProperty.isTrue(c, TOOL_WINDOW_EDGE_LEFT)
      RIGHT -> ClientProperty.isTrue(c, TOOL_WINDOW_EDGE_RIGHT)
      BOTTOM -> ClientProperty.isTrue(c, TOOL_WINDOW_EDGE_BOTTOM)
      else -> false
    }
    if (!alreadyHasBorder) {
      super.paintBorder(c, g, x, y, width, height)
    }
  }

  companion object {

    @ApiStatus.Internal
    @JvmField
    val HEADER_WITH_BORDER_ABOVE: Key<Boolean> = Key.create("HEADER_WITH_BORDER_ABOVE")

    @ApiStatus.Internal
    @JvmField
    val TOOL_WINDOW_EDGE_LEFT: Key<Boolean> = Key.create("TOOL_WINDOW_EDGE_LEFT")

    @ApiStatus.Internal
    @JvmField
    val TOOL_WINDOW_EDGE_RIGHT: Key<Boolean> = Key.create("TOOL_WINDOW_EDGE_RIGHT")

    @ApiStatus.Internal
    @JvmField
    val TOOL_WINDOW_EDGE_BOTTOM: Key<Boolean> = Key.create("TOOL_WINDOW_EDGE_BOTTOM")

    @ApiStatus.Internal
    @JvmField
    val TOOLBAR_WITH_BORDER_ABOVE: Key<Boolean> = Key.create("TOOLBAR_WITH_BORDER_ABOVE")

    @ApiStatus.Internal
    @JvmField
    val TOOLBAR_WITH_BORDER_LEFT: Key<Boolean> = Key.create("TOOLBAR_WITH_BORDER_LEFT")

    private val TARGET_COMPONENT: Key<WeakReference<JComponent>> = Key.create("ScrollableContentBorder.TARGET_COMPONENT")

    @JvmStatic
    @JvmOverloads
    fun setup(scrollPane: JScrollPane, side: Side, targetComponent: JComponent = scrollPane) {
      setup(scrollPane, setOf(side), targetComponent)
    }

    @JvmStatic
    @JvmOverloads
    fun setup(scrollPane: JScrollPane, sides: Set<Side>, targetComponent: JComponent = scrollPane) {
      ClientProperty.put(scrollPane, TARGET_COMPONENT, WeakReference(targetComponent))
      val borders = installBorders(targetComponent, sides)
      val tracker = ScrollPaneScrolledStateTracker(scrollPane) { scrollPaneState ->
        updateBorderVisibility(targetComponent, borders, scrollPaneState.state)
      }
      targetComponent.addPropertyChangeListener("border", object : PropertyChangeListener {
        override fun propertyChange(evt: PropertyChangeEvent?) {
          targetComponent.removePropertyChangeListener("border", this)
          tracker.detach()
          ClientProperty.put(scrollPane, TARGET_COMPONENT, null)
        }
      })
    }

    @JvmStatic
    fun setup(container: JComponent, side: Side) {
      setup(container, setOf(side))
    }

    @JvmStatic
    fun setup(container: JComponent, sides: Set<Side>) {
      val borders = installBorders(container, sides)
      ScrollPaneTracker(container, { true }) { tracker ->
        updateScrollPaneStates(container, borders, tracker)
      }
    }

    private fun installBorders(targetComponent: JComponent, sides: Set<Side>): Map<Side, ScrollableContentBorder> {
      val borders = sides.associateWith { side -> ScrollableContentBorder(side.toMask()) }
      targetComponent.border = if (borders.size == 1) borders.values.single() else JBUI.Borders.compound(*borders.values.toTypedArray())
      return borders
    }

    private fun updateScrollPaneStates(
      container: JComponent,
      borders: Map<Side, ScrollableContentBorder>,
      tracker: ScrollPaneTracker,
    ) {
      for (scrollPaneState in tracker.scrollPaneStates) {
        ClientProperty.put(scrollPaneState.scrollPane, TARGET_COMPONENT, WeakReference(container))
      }
      val state = ScrolledState(
        isHorizontalAtStart = tracker.noneMatch { atLeft(container, it.scrollPane) && !it.state.isHorizontalAtStart },
        isHorizontalAtEnd = tracker.noneMatch { atRight(container, it.scrollPane) && !it.state.isHorizontalAtEnd },
        isVerticalAtStart = tracker.noneMatch { atTop(container, it.scrollPane) && !it.state.isVerticalAtStart },
        isVerticalAtEnd = tracker.noneMatch { atBottom(container, it.scrollPane) && !it.state.isVerticalAtEnd },
      )
      updateBorderVisibility(container, borders, state)
    }

    private fun ScrollPaneTracker.noneMatch(predicate: (ScrollPaneScrolledState) -> Boolean): Boolean =
      scrollPaneStates.none { it.scrollPane.isShowing && predicate(it) }

    private fun atLeft(container: JComponent, scrollPane: JScrollPane): Boolean =
      SwingUtilities.convertPoint(scrollPane.parent, scrollPane.location, container).x == container.insets.left

    private fun atRight(container: JComponent, scrollPane: JScrollPane): Boolean =
      SwingUtilities.convertRectangle(scrollPane.parent, scrollPane.bounds, container).let { it.x + it.width } == container.width - container.insets.right

    private fun atTop(container: JComponent, scrollPane: JScrollPane): Boolean =
      SwingUtilities.convertPoint(scrollPane.parent, scrollPane.location, container).y == container.insets.top

    private fun atBottom(container: JComponent, scrollPane: JScrollPane): Boolean =
      SwingUtilities.convertRectangle(scrollPane.parent, scrollPane.bounds, container).let { it.y + it.height } == container.height - container.insets.bottom

    private fun isOneSideBorder(sideBorder: Border): Boolean {
      if (sideBorder !is SideBorder) return false

      return with(sideBorder.getBorderInsets(null)) {
        sequenceOf(top, bottom, right, left).filter { it != 0 }.count() == 1
      }
    }

    @JvmStatic
    fun getTargetComponent(scrollPane: JScrollPane): JComponent? = ClientProperty.get(scrollPane, TARGET_COMPONENT)?.get()
  }
}

private fun updateBorderVisibility(
  targetComponent: JComponent,
  borders: Map<Side, ScrollableContentBorder>,
  state: ScrolledState,
) {
  var changed = false
  for ((side, border) in borders) {
    val scrolled = !when (side) {
      Side.TOP -> state.isVerticalAtStart
      Side.BOTTOM -> state.isVerticalAtEnd
      Side.LEFT -> state.isHorizontalAtStart
      Side.RIGHT -> state.isHorizontalAtEnd
    }
    val visible = scrolled || !ExperimentalUI.isNewUI()
    val wasVisible = border.isVisible()
    border.setVisible(visible)
    if (visible != wasVisible) {
      changed = true
    }
  }
  if (changed) {
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
