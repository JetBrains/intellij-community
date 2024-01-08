// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.toolbarLayout

import com.intellij.openapi.actionSystem.ActionToolbar
import java.awt.Component
import java.awt.Dimension
import java.awt.Insets
import java.awt.Rectangle
import java.lang.Boolean
import javax.swing.JComponent
import kotlin.Int

class RightActionsAdjusterStrategyWrapper(private val delegate: ToolbarLayoutStrategy): ToolbarLayoutStrategy {

  override fun calculateBounds(size2Fit: Dimension, toolbar: ActionToolbar): MutableList<Rectangle> {
    val bounds = delegate.calculateBounds(size2Fit, toolbar)

    val component = toolbar.component
    val componentCount = component.componentCount
    if (componentCount > 0 && size2Fit.width < Int.MAX_VALUE) {
      val insets: Insets = component.insets
      var rightOffset = insets.right
      var i = componentCount - 1
      var j = 1
      while (i > 0) {
        val child: Component = component.getComponent(i)
        if (child is JComponent && child.getClientProperty(RIGHT_ALIGN_KEY) === Boolean.TRUE) {
          rightOffset += bounds.get(i).width
          val r: Rectangle = bounds.get(bounds.size - j)
          r.x = size2Fit.width - rightOffset
        }
        i--
        j++
      }
    }
    return bounds
  }

}