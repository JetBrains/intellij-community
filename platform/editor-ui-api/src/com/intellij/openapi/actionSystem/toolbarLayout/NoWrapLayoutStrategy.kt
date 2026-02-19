// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.toolbarLayout

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.SwingConstants

internal class NoWrapLayoutStrategy(private val myAdjustTheSameSize: Boolean) : ToolbarLayoutStrategy {

  private fun doCalculateBounds(toolbar: ActionToolbar): List<Rectangle> {

    val toolbarComponent = toolbar.component
    val componentsCount = toolbarComponent.componentCount
    val width = toolbarComponent.width
    val height = toolbarComponent.height
    val insets: Insets = toolbarComponent.insets
    val orientation = toolbar.orientation

    val res = List(componentsCount) { Rectangle() }

    if (myAdjustTheSameSize) {
      val maxWidth: Int = maxComponentPreferredWidth(toolbarComponent)
      val maxHeight: Int = maxComponentPreferredHeight(toolbarComponent)

      var offset = 0
      if (orientation == SwingConstants.HORIZONTAL) {
        for (i in 0 until componentsCount) {
          val r: Rectangle = res[i]
          r.setBounds(insets.left + offset, insets.top + (height - maxHeight) / 2, maxWidth, maxHeight)
          offset += maxWidth
        }
      }
      else {
        for (i in 0 until componentsCount) {
          val r: Rectangle = res[i]
          r.setBounds(insets.left + (width - maxWidth) / 2, insets.top + offset, maxWidth, maxHeight)
          offset += maxHeight
        }
      }
    }
    else {
      if (orientation == SwingConstants.HORIZONTAL) {
        val maxHeight: Int = maxComponentPreferredHeight(toolbarComponent)
        var offset = 0
        for (i in 0 until componentsCount) {
          val d: Dimension = getChildPreferredSize(toolbarComponent, i)
          val r: Rectangle = res[i]
          r.setBounds(insets.left + offset, insets.top + (maxHeight - d.height) / 2, d.width, d.height)
          offset += d.width
        }
      }
      else {
        val maxWidth: Int = maxComponentPreferredWidth(toolbarComponent)
        var offset = 0
        for (i in 0 until componentsCount) {
          val d: Dimension = getChildPreferredSize(toolbarComponent, i)
          val r: Rectangle = res[i]
          r.setBounds(insets.left + (maxWidth - d.width) / 2, insets.top + offset, d.width, d.height)
          offset += d.height
        }
      }
    }

    return res
  }

  override fun calculateBounds(toolbar: ActionToolbar): List<Rectangle> {
    return doCalculateBounds(toolbar)
  }

  override fun calcPreferredSize(toolbar: ActionToolbar): Dimension {
    val bounds = doCalculateBounds(toolbar)
    if (bounds.isEmpty()) return JBUI.emptySize()

    val dimension = bounds.reduce { acc, rect -> acc.union(rect) }.size
    JBInsets.addTo(dimension, toolbar.component.insets)

    return dimension
  }

  override fun calcMinimumSize(toolbar: ActionToolbar): Dimension {
    return JBUI.emptySize()
  }
}

