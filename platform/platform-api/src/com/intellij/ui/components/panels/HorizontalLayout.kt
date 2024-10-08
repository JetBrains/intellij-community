// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.panels

import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBValue
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.*
import java.util.function.IntSupplier
import javax.swing.SwingConstants
import kotlin.math.abs

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
class HorizontalLayout private constructor(private val gap: JBValue,
                                           private val verticalAlignment: Int = FILL) : LayoutManager2 {
  enum class Group {
    LEFT, CENTER, RIGHT
  }

  @Internal
  var preferredSizeFunction: (Component) -> Dimension = { LayoutUtil.getPreferredSize(it) }

  private val leftGroup = ArrayList<Component>()
  private val centerGroup = ArrayList<Component>()
  private val rightGroup = ArrayList<Component>()

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
  constructor(gap: Int, alignment: Int = FILL) : this(gap = if (gap <= 0) JBValue.Float.EMPTY else JBValue.Float(gap.toFloat()),
                                                      verticalAlignment = alignment)

  init {
    check(verticalAlignment == FILL ||
          verticalAlignment == SwingConstants.TOP ||
          verticalAlignment == SwingConstants.BOTTOM ||
          verticalAlignment == SwingConstants.CENTER) {
      "unsupported alignment: $verticalAlignment"
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
    when (constraints) {
      is Group -> addLayoutComponent(component, constraints)
      null -> addLayoutComponent(LEFT, component)
      is String -> addLayoutComponent(constraints as? String, component)
      else -> throw IllegalArgumentException("unsupported constraints: $constraints")
    }
  }

  override fun maximumLayoutSize(target: Container): Dimension = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

  override fun getLayoutAlignmentX(target: Container): Float = .5f

  override fun getLayoutAlignmentY(target: Container): Float = .5f

  override fun invalidateLayout(target: Container) {
  }

  override fun addLayoutComponent(name: String?, component: Component) {
    addLayoutComponent(component, group = when {
      name == null || LEFT.equals(name, ignoreCase = true) -> Group.LEFT
      CENTER.equals(name, ignoreCase = true) -> Group.CENTER
      RIGHT.equals(name, ignoreCase = true) -> Group.RIGHT
      else -> throw IllegalArgumentException("unsupported name: $name")
    })
  }

  private fun addLayoutComponent(component: Component, group: Group) {
    synchronized(component.treeLock) {
      when (group) {
        Group.LEFT -> leftGroup
        Group.CENTER -> centerGroup
        Group.RIGHT -> rightGroup
      }.add(component)
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
        leftX = gap + layout(list = leftGroup, startX = 0, height = height, insets = insets)
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
        centerX = gap + layout(list = centerGroup, startX = centerX, height = height, insets = insets)
        if (rightX < centerX) {
          rightX = centerX
        }
      }
      if (right != null) {
        layout(list = rightGroup, startX = rightX, height = height, insets = insets)
      }
    }
  }

  private fun layout(list: List<Component>, startX: Int, height: Int, insets: Insets): Int {
    var x = startX
    val gap = gap.get()
    for (component in list) {
      if (!component.isVisible) {
        continue
      }

      val size = preferredSizeFunction(component)
      var y = 0
      if (verticalAlignment == FILL) {
        size.height = height
      }
      else if (verticalAlignment != SwingConstants.TOP) {
        y = height - size.height
        if (verticalAlignment == SwingConstants.CENTER) {
          y /= 2
        }
      }

      val width = size.width
      component.setBounds(x + insets.left, y + insets.top, width, size.height)
      x += width + gap
    }
    return x
  }

  private fun getPreferredSize(list: List<Component>): Dimension? {
    val gap = gap.get()
    var result: Dimension? = null
    for (component in list) {
      if (component.isVisible) {
        result = joinDimension(result, gap, preferredSizeFunction(component))
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