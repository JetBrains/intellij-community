// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.list

import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import javax.accessibility.AccessibleContext
import javax.swing.JPanel

/**
 * Allows to paint selection according to [selectionArc], [selectionColor] and [selectionInsets]
 */
@ApiStatus.Experimental
open class SelectablePanel(background: Color? = null) : JPanel() {

  enum class SelectionArcCorners {
    /**
     * Arc corners for top-left and bottom-left corners
     */
    LEFT,

    /**
     * Arc corners for top-right and bottom-right corners
     */
    RIGHT,

    /**
     * All corners are rounded
     */
    ALL
  }

  companion object {
    @JvmStatic
    @JvmOverloads
    fun wrap(component: Component, background: Color? = null): SelectablePanel {
      val result = object : SelectablePanel(background) {
        override fun getAccessibleContext(): AccessibleContext {
          return component.accessibleContext
        }
      }
      result.layout = BorderLayout()
      result.add(component, BorderLayout.CENTER)
      return result
    }
  }

  var selectionArc: Int = 0
  var selectionArcCorners = SelectionArcCorners.ALL
  var selectionColor: Color? = null
  var selectionInsets: Insets = JBUI.emptyInsets()
  var preferredHeight: Int? = null
    set(value) {
      field = value
      invalidate()
    }

  init {
    if (background != null) {
      this.background = background
    }
  }

  override fun getPreferredSize(): Dimension {
    val result = super.getPreferredSize()

    return if (preferredHeight == null) result else Dimension(result.width, preferredHeight!!)
  }

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D

    try  {
      background?.let {
        if (isOpaque) {
          g2.color = it
          g2.fillRect(0, 0, width, height)
        }
      }

      if (selectionColor == null || background == selectionColor) {
        return
      }

      // Paint selection
      g2.color = selectionColor
      GraphicsUtil.setupAAPainting(g2)
      var rectX = selectionInsets.left
      val rectY = selectionInsets.top
      var rectWidth = width - rectX - selectionInsets.right
      val rectHeight = height - rectY - selectionInsets.bottom

      if (selectionArc == 0 || selectionArcCorners == SelectionArcCorners.ALL) {
        g2.fillRoundRect(rectX, rectY, rectWidth, rectHeight, selectionArc, selectionArc)
      } else {
        g2.clipRect(rectX, rectY, rectWidth, rectHeight)
        when (selectionArcCorners) {
          SelectionArcCorners.LEFT -> {
            rectWidth += selectionArc
          }

          SelectionArcCorners.RIGHT -> {
            rectX -= selectionArc
            rectWidth += selectionArc
          }

          else -> {
            throw RuntimeException("No implementation for $selectionArcCorners")
          }
        }
        g2.fillRoundRect(rectX, rectY, rectWidth, rectHeight, selectionArc, selectionArc)
      }
    }
    finally {
      g2.dispose()
    }
  }
}
