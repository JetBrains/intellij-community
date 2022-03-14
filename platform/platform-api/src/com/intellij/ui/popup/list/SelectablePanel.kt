// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.list

import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics
import java.awt.Insets

/**
 * Allows to paint selection: when background color differs from [unselectedBackground] then selection is
 * painted according to selection configurations and current [background]
 */
@ApiStatus.Experimental
open class SelectablePanel(var unselectedBackground: Color? = null) : BorderLayoutPanel() {

  var selectionArc: Int = 0
  var selectionInsets: Insets = JBUI.emptyInsets()

  init {
    background = unselectedBackground
  }

  override fun paintComponent(g: Graphics) {
    unselectedBackground?.let {
      g.color = it
      g.fillRect(0, 0, width, height)
    }

    if (background == unselectedBackground) {
      return
    }

    // Paint selection
    g.color = background
    val config = GraphicsUtil.setupAAPainting(g)
    g.fillRoundRect(selectionInsets.left, selectionInsets.top,
                    width - selectionInsets.left - selectionInsets.right,
                    height - selectionInsets.top - selectionInsets.bottom, selectionArc, selectionArc)
    config.restore()
  }
}
