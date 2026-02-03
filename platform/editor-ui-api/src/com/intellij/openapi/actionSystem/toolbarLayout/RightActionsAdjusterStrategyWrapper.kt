// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.toolbarLayout

import com.intellij.openapi.actionSystem.ActionToolbar
import java.awt.Component
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JComponent

internal class RightActionsAdjusterStrategyWrapper(private val delegate: ToolbarLayoutStrategy): ToolbarLayoutStrategy by delegate {

  override fun calculateBounds(toolbar: ActionToolbar): MutableList<Rectangle> {
    val bounds = delegate.calculateBounds(toolbar)

    val component = toolbar.component
    val componentCount = component.componentCount
    val componentWidth = component.width

    if (componentCount > 0 ) {
      val insets: Insets = component.insets
      var rightOffset = insets.right
      var i = componentCount - 1
      var j = 1
      while (i > 0) {
        val child: Component = component.getComponent(i)
        if (child is JComponent && child.getClientProperty(RIGHT_ALIGN_KEY) == true) {
          rightOffset += bounds.get(i).width
          val r: Rectangle = bounds.get(bounds.size - j)
          r.x = componentWidth - rightOffset
        }
        i--
        j++
      }
    }

    return bounds
  }

}