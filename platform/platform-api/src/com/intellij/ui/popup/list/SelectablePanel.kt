// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.list

import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.*
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
      val result = SelectablePanel(background)
      result.layout = BorderLayout()
      result.add(component, BorderLayout.CENTER)
      return result
    }
  }

  var selectionArc: Int = 0
  var selectionArcCorners = SelectionArcCorners.ALL
  var selectionColor: Color? = null
  var selectionInsets: Insets = JBUI.emptyInsets()

  init {
    if (background != null) {
      this.background = background
    }
  }

  override fun paintComponent(g: Graphics) {
    background?.let {
      if (isOpaque) {
        g.color = it
        g.fillRect(0, 0, width, height)
      }
    }

    if (selectionColor == null || background == selectionColor) {
      return
    }

    // Paint selection
    g.color = selectionColor
    val config = GraphicsUtil.setupAAPainting(g)
    val rectX = selectionInsets.left
    val rectY = selectionInsets.top
    val rectWidth = width - rectX - selectionInsets.right
    val rectHeight = height - rectY - selectionInsets.bottom
    g.fillRoundRect(rectX, rectY, rectWidth, rectHeight, selectionArc, selectionArc)

    if (selectionArc != 0) {
      when (selectionArcCorners) {
        SelectionArcCorners.LEFT -> {
          g.fillRect(rectX + rectWidth - selectionArc, rectY, selectionArc, rectHeight)
        }

        SelectionArcCorners.RIGHT -> {
          g.fillRect(rectX, rectY, selectionArc, rectHeight)
        }

        SelectionArcCorners.ALL -> {}
      }
    }
    config.restore()
  }
}
