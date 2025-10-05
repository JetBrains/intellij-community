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

  internal enum class Side {
    TOP, BOTTOM, LEFT, RIGHT
  }

  enum class SelectionArcCorners(internal val sides: Set<Side>) {
    NONE(emptySet()),

    TOP_LEFT(setOf(Side.TOP, Side.LEFT)),
    TOP_RIGHT(setOf(Side.TOP, Side.RIGHT)),
    BOTTOM_LEFT(setOf(Side.BOTTOM, Side.LEFT)),
    BOTTOM_RIGHT(setOf(Side.BOTTOM, Side.RIGHT)),

    /**
     * Arc corners for top-left and bottom-left corners
     */
    LEFT(setOf(Side.LEFT, Side.TOP, Side.BOTTOM)),

    /**
     * Arc corners for top-right and bottom-right corners
     */
    RIGHT(setOf(Side.TOP, Side.BOTTOM, Side.RIGHT)),
    TOP(setOf(Side.TOP, Side.LEFT, Side.RIGHT)),
    BOTTOM(setOf(Side.BOTTOM, Side.LEFT, Side.RIGHT)),

    /**
     * All corners are rounded
     */
    ALL(Side.values().toSet())
  }

  companion object {
    @JvmStatic
    @JvmOverloads
    fun wrap(component: Component, background: Color? = null): SelectablePanel {
      val result = SelectablePanel(background)
      result.accessibleContextProvider = component
      result.layout = BorderLayout()
      result.add(component, BorderLayout.CENTER)
      return result
    }
  }

  var selectionArc: Int = 0
  var selectionArcCorners: SelectionArcCorners = SelectionArcCorners.ALL
  var selectionColor: Color? = null
    set(value) {
      if (field != value) {
        field = value
        repaint()
      }
    }
  var selectionInsets: Insets = JBUI.emptyInsets()
  var preferredHeight: Int? = null
    set(value) {
      field = value
      invalidate()
    }
  var preferredWidth: Int? = null
    set(value) {
      field = value
      invalidate()
    }

  @ApiStatus.Experimental
  var accessibleContextProvider: Component? = null

  init {
    if (background != null) {
      this.background = background
    }
  }

  override fun getAccessibleContext(): AccessibleContext {
    accessibleContextProvider?.let {
      return it.accessibleContext
    }
    return super.getAccessibleContext()
  }

  override fun getPreferredSize(): Dimension {
    val preferredWidth = preferredWidth
    val preferredHeight = preferredHeight

    if (preferredWidth != null && preferredHeight != null) {
      return Dimension(preferredWidth, preferredHeight)
    }

    val preferredSize = super.getPreferredSize()
    return Dimension(preferredWidth ?: preferredSize.width, preferredHeight ?: preferredSize.height)
  }

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D

    try {
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
      var rectY = selectionInsets.top
      var rectWidth = width - rectX - selectionInsets.right
      var rectHeight = height - rectY - selectionInsets.bottom

      if (selectionArc == 0 || selectionArcCorners == SelectionArcCorners.ALL) {
        g2.fillRoundRect(rectX, rectY, rectWidth, rectHeight, selectionArc, selectionArc)
      }
      else {
        g2.clipRect(rectX, rectY, rectWidth, rectHeight)
        rectX -= selectionArc
        rectY -= selectionArc
        rectHeight += 2 * selectionArc
        rectWidth += 2 * selectionArc

        for (side in selectionArcCorners.sides) {
          when (side) {
            Side.TOP -> {
              rectY += selectionArc
              rectHeight -= selectionArc
            }
            Side.BOTTOM -> {
              rectHeight -= selectionArc
            }
            Side.LEFT -> {
              rectX += selectionArc
              rectWidth -= selectionArc
            }
            Side.RIGHT -> {
              rectWidth -= selectionArc
            }
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
