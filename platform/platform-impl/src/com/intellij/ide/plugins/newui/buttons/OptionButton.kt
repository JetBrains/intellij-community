// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui.buttons

import com.intellij.ui.components.JBOptionButton
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JButton

@ApiStatus.Internal
open class OptionButton @JvmOverloads constructor(
  action: Action? = null,
  options: Array<Action>? = null
) : JBOptionButton(action, options) {

  private val myBaseline = JButton()

  init {
    addSeparator = false
    selectFirstItem = false
    popupBackgroundColor = UIUtil.getListBackground()
    showPopupYOffset = -2

    popupHandler =  {
      val size = Dimension(it.size)
      val insets = insets
      val oldWidth = size.width
      val newWidth = width - insets.left - insets.right
      if (oldWidth <= newWidth || newWidth / oldWidth.toDouble() > 0.85) {
        size.width = newWidth
      }
      it.size = size
      null
    }
  }

  override fun getBaseline(width: Int, height: Int): Int {
    myBaseline.text = text
    myBaseline.size = size
    return myBaseline.getBaseline(width, height)
  }
}