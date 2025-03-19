// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.toolbarLayout

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.JComponent
import javax.swing.SwingConstants
import kotlin.math.max

internal class WrapLayoutStrategy(private val myAdjustTheSameSize: Boolean): ToolbarLayoutStrategy {

  private val fallbackDelegate = NoWrapLayoutStrategy(myAdjustTheSameSize)

  override fun calculateBounds(toolbar: ActionToolbar): List<Rectangle> {

    // We have to graceful handle case when toolbar was not laid out yet.
    // In this case we calculate bounds as it is a NOWRAP toolbar.
    val component = toolbar.component
    if (component.width == 0 || component.height == 0) {
      return fallbackDelegate.calculateBounds(toolbar)
    }

    return doCalculateBounds(component.size, toolbar)
  }

  override fun calcPreferredSize(toolbar: ActionToolbar): Dimension {
    val component = toolbar.component
    // In case when toolbar was not laid out yet we calculate preferred size as it is a NOWRAP toolbar.
    if (component.width == 0) return fallbackDelegate.calcPreferredSize(toolbar)

    val bounds = doCalculateBounds(Dimension(component.width, Int.MAX_VALUE), toolbar)
    val dimension = if (bounds.isEmpty()) Dimension(0, 0)
                    else bounds.reduce { acc, rect -> acc.union(rect) }.size
    JBInsets.addTo(dimension, toolbar.component.insets)

    return dimension
  }

  override fun calcMinimumSize(toolbar: ActionToolbar): Dimension {
    return JBUI.emptySize()
  }

  private fun doCalculateBounds(size2Fit: Dimension, toolbar: ActionToolbar): List<Rectangle> {
    val component = toolbar.component
    val componentsCount = component.componentCount
    val insets: Insets = component.insets
    val widthToFit: Int = size2Fit.width - insets.left - insets.right
    val heightToFit: Int = size2Fit.height - insets.top - insets.bottom
    val minimumButtonSize = toolbar.minimumButtonSize
    val orientation = toolbar.orientation

    val res = List(componentsCount) { Rectangle() }

    if (myAdjustTheSameSize) {
      val maxWidth: Int = maxComponentPreferredWidth(component)
      val maxHeight: Int = maxComponentPreferredHeight(component)
      var xOffset = 0
      var yOffset = 0
      if (orientation == SwingConstants.HORIZONTAL) {
        // Lay components out

        val maxRowWidth: Int = getMaxRowWidth(component, widthToFit, maxWidth)
        for (i in 0 until componentsCount) {
          if (xOffset + maxWidth > maxRowWidth) { // place component at new row
            xOffset = 0
            yOffset += maxHeight
          }

          val each: Rectangle = res.get(i)
          each.setBounds(insets.left + xOffset, insets.top + yOffset, maxWidth, maxHeight)

          xOffset += maxWidth
        }
      }
      else {
        // Lay components out
        // Calculate max size of a row. It's not possible to make more then 3 column toolbar

        val maxRowHeight = max(heightToFit, (componentsCount * minimumButtonSize.height / 3))
        for (i in 0 until componentsCount) {
          if (yOffset + maxHeight > maxRowHeight) { // place component at new row
            yOffset = 0
            xOffset += maxWidth
          }

          val each: Rectangle = res.get(i)
          each.setBounds(insets.left + xOffset, insets.top + yOffset, maxWidth, maxHeight)

          yOffset += maxHeight
        }
      }
    }
    else {
      if (orientation == SwingConstants.HORIZONTAL) {
        // Calculate row height
        var rowHeight = 0
        val dims = arrayOfNulls<Dimension>(componentsCount) // we will use this dimensions later
        for (i in 0 until componentsCount) {
          dims[i] = getChildPreferredSize(component, i)
          val height = dims[i]!!.height
          rowHeight = max(rowHeight, height)
        }

        // Lay components out
        var xOffset = 0
        var yOffset = 0
        // Calculate max size of a row. It's not possible to make more then 3 row toolbar
        val maxRowWidth: Int = getMaxRowWidth(component, widthToFit, minimumButtonSize.width)

        for (i in 0 until componentsCount) {
          val d = dims[i]
          if (xOffset + d!!.width > maxRowWidth) { // place component at new row
            xOffset = 0
            yOffset += rowHeight
          }

          val each: Rectangle = res.get(i)
          each.setBounds(insets.left + xOffset, insets.top + yOffset + (rowHeight - d.height) / 2, d.width, d.height)

          xOffset += d.width
        }
      }
      else {
        // Calculate row width
        var rowWidth = 0
        val dims = arrayOfNulls<Dimension>(componentsCount) // we will use this dimensions later
        for (i in 0 until componentsCount) {
          dims[i] = getChildPreferredSize(component, i)
          val width = dims[i]!!.width
          rowWidth = max(rowWidth, width)
        }

        // Lay components out
        var xOffset = 0
        var yOffset = 0
        // Calculate max size of a row. It's not possible to make more then 3 column toolbar
        val maxRowHeight = max(heightToFit, (componentsCount * minimumButtonSize.height / 3))
        for (i in 0 until componentsCount) {
          val d = dims[i]
          if (yOffset + d!!.height > maxRowHeight) { // place component at new row
            yOffset = 0
            xOffset += rowWidth
          }

          val each: Rectangle = res.get(i)
          each.setBounds(insets.left + xOffset + (rowWidth - d.width) / 2, insets.top + yOffset, d.width, d.height)

          yOffset += d.height
        }
      }
    }

    return res
  }

  private fun getMaxRowWidth(parent: Container, widthToFit: Int, maxWidth: Int): Int {
    val componentCount: Int = parent.componentCount
    // Calculate max size of a row. It's not possible to make more than 3 row toolbar
    var maxRowWidth = max(widthToFit, (componentCount * maxWidth / 3))
    for (i in 0 until componentCount) {
      val component: Component = parent.getComponent(i)
      if (component is JComponent && component.getClientProperty(RIGHT_ALIGN_KEY) == true) {
        maxRowWidth -= getChildPreferredSize(parent, i).width
      }
    }
    return maxRowWidth
  }
}