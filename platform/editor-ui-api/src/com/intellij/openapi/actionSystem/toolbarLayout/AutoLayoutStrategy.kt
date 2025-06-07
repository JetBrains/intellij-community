// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.toolbarLayout

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Dimension
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.SwingConstants
import kotlin.math.max

internal class AutoLayoutStrategy(private val myForceShowFirstComponent: Boolean, private val myNoGapMode: Boolean): ToolbarLayoutStrategy {

  private val expandIcon = AllIcons.Ide.Link

  override fun calculateBounds(toolbar: ActionToolbar): List<Rectangle> {
    return doCalculateBounds(toolbar.component.size, toolbar)
  }

  override fun calcPreferredSize(toolbar: ActionToolbar): Dimension {
    if (toolbar.component.componentCount == 0) return JBUI.emptySize()

    val bounds = doCalculateBounds(Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE), toolbar)
    val size = bounds.filter { it.x != Int.MAX_VALUE }.reduce { acc, rect -> acc.union(rect) }.size
    JBInsets.addTo(size, toolbar.component.insets)
    return size
  }

  override fun calcMinimumSize(toolbar: ActionToolbar): Dimension {
    var childrenCount = toolbar.component.componentCount
    if (childrenCount == 0) return JBUI.emptySize()

    val dimension = Dimension()

    if (myForceShowFirstComponent) {
      val firstChildSize = toolbar.component.components.firstOrNull { it.isVisible }?.preferredSize
      firstChildSize?.let { size ->
        if (toolbar.orientation == SwingConstants.HORIZONTAL) {
          dimension.width += size.width
          dimension.height = max(dimension.height, size.height)
        }
        else {
          dimension.height += size.height
          dimension.width = max(dimension.width, size.width)
        }
      }
      childrenCount--
    }

    if (childrenCount > 0) {
      val minimumButtonSize = toolbar.minimumButtonSize
      if (toolbar.orientation == SwingConstants.HORIZONTAL) {
        dimension.width += expandIcon.iconWidth
        dimension.height = max(max(dimension.height, expandIcon.iconHeight), minimumButtonSize.height)
      }
      else {
        dimension.height += expandIcon.iconHeight
        dimension.width = max(max(dimension.width, expandIcon.iconWidth), minimumButtonSize.width)
      }
    }

    JBInsets.addTo(dimension, toolbar.component.insets)
    return dimension
  }

  private fun doCalculateBounds(size2Fit: Dimension, toolbar: ActionToolbar): List<Rectangle> {

    val component = toolbar.component
    val componentCount = component.componentCount
    val insets: Insets = component.insets

    val res = List(componentCount) { Rectangle() }

    val autoButtonSize = if (toolbar.isReservePlaceAutoPopupIcon) Dimension(expandIcon.iconWidth, expandIcon.iconHeight) else Dimension()
    var full = false

    val widthToFit: Int = size2Fit.width - insets.left - insets.right
    val heightToFit: Int = size2Fit.height - insets.top - insets.bottom

    if (toolbar.orientation == SwingConstants.HORIZONTAL) {
      var eachX = 0
      var maxHeight = heightToFit
      for (i in 0 until componentCount) {
        val eachComp: Component = component.getComponent(i)
        val isLast = i == componentCount - 1

        val eachBound = Rectangle(getChildPreferredSize(component, i))
        maxHeight = max(eachBound.height, maxHeight)

        if (!full) {
          val inside = if (isLast) eachX + eachBound.width <= widthToFit else eachX + eachBound.width + autoButtonSize.width <= widthToFit

          if (inside) {
            val isSecondaryButton = (eachComp as? JComponent)?.getClientProperty(ActionToolbar.SECONDARY_ACTION_PROPERTY) == true
            if (isSecondaryButton) {
              assert(isLast)
              if (size2Fit.width != Int.MAX_VALUE && !myNoGapMode) {
                eachBound.x = size2Fit.width - insets.right - eachBound.width
                eachX = eachBound.maxX.toInt() - insets.left
              }
              else {
                eachBound.x = insets.left + eachX
              }
            }
            else {
              eachBound.x = insets.left + eachX
              eachX += eachBound.width
            }
            eachBound.y = insets.top
          }
          else {
            full = true
          }
        }

        if (full) {
          eachBound.x = Int.MAX_VALUE
          eachBound.y = Int.MAX_VALUE
        }

        res.get(i).setBounds(eachBound)
      }

      for (r in res) {
        if (r.height < maxHeight) {
          r.y += (maxHeight - r.height) / 2
        }
      }
    }
    else {
      var eachY = 0
      for (i in 0 until componentCount) {
        val eachBound = Rectangle(getChildPreferredSize(component, i))
        if (!full) {
          val outside = if (i < componentCount - 1) {
            eachY + eachBound.height + autoButtonSize.height <= heightToFit
          }
          else {
            eachY + eachBound.height <= heightToFit
          }
          if (outside) {
            eachBound.x = insets.left
            eachBound.y = insets.top + eachY
            eachY += eachBound.height
          }
          else {
            full = true
          }
        }

        if (full) {
          eachBound.x = Int.MAX_VALUE
          eachBound.y = Int.MAX_VALUE
        }

        res.get(i).setBounds(eachBound)
      }
    }

    return res
  }
}