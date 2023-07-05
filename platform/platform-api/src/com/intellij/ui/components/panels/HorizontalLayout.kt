// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.panels

import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBValue
import java.awt.*
import javax.swing.SwingConstants
import kotlin.math.abs
import kotlin.math.max

/**
 * This class is intended to lay out added components horizontally.
 * It allows adding them into the LEFT, CENTER, or RIGHT group, which are aligned separately.
 * Every group can contain any number of components. The specified gap is added between components,
 * and the double gap is added between groups of components. The gap will be scaled automatically.
 *
 * **NB!: this class must be modified together with the `VerticalLayout` class accordingly**
 *
 * For simpler cases without groups `ListLayout` should be better.
 *
 * @see VerticalLayout
 * @see ListLayout
 */
class HorizontalLayout(private val gap: JBValue, private val alignment: Int = 0) : LayoutManager2 {
  private val leftGroup = ArrayList<Component>()
  private val rightGroup = ArrayList<Component>()
  private val centerGroup = ArrayList<Component>()

  /**
   * Creates a layout with the specified gap and vertical alignment.
   * All components will have preferred sizes, but their heights will be set according to the container (when alignment is set to `FILL`).
   * The gap will be scaled automatically.
   *
   * @param gap       horizontal gap between components, without DPI scaling
   * @param alignment vertical alignment for components
   *
   * @see SwingConstants.TOP
   * @see SwingConstants.BOTTOM
   * @see SwingConstants.CENTER
   */
  @JvmOverloads
  constructor(gap: Int, alignment: Int = FILL) : this(gap = JBValue.Float(max(0.0, gap.toDouble()).toFloat()), alignment = alignment)

  init {
    check(alignment == FILL ||
          alignment == SwingConstants.TOP || alignment == SwingConstants.BOTTOM || alignment == SwingConstants.CENTER) {
      "unsupported alignment: $alignment"
    }
  }

  companion object {
    const val FILL: Int = -1
    const val LEFT: String = "LEFT"
    const val RIGHT: String = "RIGHT"
    const val CENTER: String = "CENTER"
  }

  fun components(): Sequence<Component> = sequenceOf(leftGroup, centerGroup, rightGroup).flatten()

  override fun addLayoutComponent(component: Component, constraints: Any?) {
    if (constraints == null || constraints is String) {
      addLayoutComponent(constraints as String, component)
    }
    else {
      throw IllegalArgumentException("unsupported constraints: $constraints")
    }
  }

  override fun maximumLayoutSize(target: Container): Dimension = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

  override fun getLayoutAlignmentX(target: Container): Float = .5f

  override fun getLayoutAlignmentY(target: Container): Float = .5f

  override fun invalidateLayout(target: Container) {
  }

  override fun addLayoutComponent(name: String?, component: Component) {
    synchronized(component.treeLock) {
      when {
        name == null || LEFT.equals(name, ignoreCase = true) -> leftGroup.add(component)
        CENTER.equals(name, ignoreCase = true) -> centerGroup.add(component)
        RIGHT.equals(name, ignoreCase = true) -> rightGroup.add(component)
        else -> throw IllegalArgumentException("unsupported name: $name")
      }
    }
  }

  override fun removeLayoutComponent(component: Component) {
    leftGroup.remove(component)
    rightGroup.remove(component)
    centerGroup.remove(component)
  }

  override fun preferredLayoutSize(container: Container): Dimension = getPreferredSize(container = container, aligned = true)

  override fun minimumLayoutSize(container: Container): Dimension = getPreferredSize(container = container, aligned = false)

  override fun layoutContainer(container: Container) {
    val gap = gap.get()
    synchronized(container.treeLock) {
      val left = getPreferredSize(leftGroup)
      val right = getPreferredSize(rightGroup)
      val center = getPreferredSize(centerGroup)
      val insets = container.insets
      val width = container.width - insets.left - insets.right
      val height = container.height - insets.top - insets.bottom
      var leftX = 0
      if (left != null) {
        leftX = gap + layout(leftGroup, 0, height, insets)
      }
      var rightX = width
      if (right != null) {
        rightX -= right.width
      }
      if (rightX < leftX) {
        rightX = leftX
      }
      if (center != null) {
        var centerX = (width - center.width) / 2
        if (centerX > leftX) {
          val centerRightX = centerX + center.width + gap + gap
          if (centerRightX > rightX) {
            centerX = rightX - center.width - gap - gap
          }
        }
        if (centerX < leftX) {
          centerX = leftX
        }
        centerX = gap + layout(centerGroup, centerX, height, insets)
        if (rightX < centerX) {
          rightX = centerX
        }
      }
      if (right != null) {
        layout(rightGroup, rightX, height, insets)
      }
    }
  }

  private fun layout(list: List<Component>, x: Int, height: Int, insets: Insets): Int {
    @Suppress("NAME_SHADOWING")
    var x = x
    val gap = gap.get()
    for (component in list) {
      if (component.isVisible) {
        val size = LayoutUtil.getPreferredSize(component)
        var y = 0
        if (alignment == FILL) {
          size.height = height
        }
        else if (alignment != SwingConstants.TOP) {
          y = height - size.height
          if (alignment == SwingConstants.CENTER) {
            y /= 2
          }
        }
        component.setBounds(x + insets.left, y + insets.top, size.width, size.height)
        x += size.width + gap
      }
    }
    return x
  }

  private fun getPreferredSize(list: List<Component>): Dimension? {
    val gap = gap.get()
    var result: Dimension? = null
    for (component in list) {
      if (component.isVisible) {
        result = joinDimension(result, gap, LayoutUtil.getPreferredSize(component))
      }
    }
    return result
  }

  private fun getPreferredSize(container: Container, aligned: Boolean): Dimension {
    val gap2 = 2 * gap.get()
    synchronized(container.treeLock) {
      val left = getPreferredSize(leftGroup)
      val right = getPreferredSize(rightGroup)
      val center = getPreferredSize(centerGroup)
      var result = joinDimension(joinDimension(joinDimension(null, gap2, left), gap2, center), gap2, right)
      if (result == null) {
        result = Dimension()
      }
      else if (aligned && center != null) {
        val leftWidth = left?.width ?: 0
        val rightWidth = right?.width ?: 0
        result.width += abs(leftWidth - rightWidth)
      }
      JBInsets.addTo(result, container.insets)
      return result
    }
  }
}

private fun joinDimension(result: Dimension?, gap: Int, size: Dimension?): Dimension? {
  if (size == null) {
    return result
  }
  if (result == null) {
    return Dimension(size)
  }

  result.width += gap + size.width
  if (result.height < size.height) {
    result.height = size.height
  }
  return result
}