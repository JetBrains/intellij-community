// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.scale.JBUIScale
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.Icon
import javax.swing.JButton

@ApiStatus.Internal
class DarculaDisclosureButton : JButton() {

  var rightIcon: Icon? = null
    set(value) {
      if (field !== value) {
        field = value
        revalidate()
        repaint()
      }
    }

  var arrowIcon: Icon? = AllIcons.General.ChevronRight
    set(value) {
      if (field !== value) {
        field = value
        revalidate()
        repaint()
      }
    }

  var buttonBackground: Color? = null
    set(value) {
      if (field != value) {
        field = value
        revalidate()
        repaint()
      }
    }

  init {
    setUI(DarculaDisclosureButtonUI())
    horizontalAlignment = LEFT
    iconTextGap = JBUIScale.scale(12)
    isRolloverEnabled = true
  }

  override fun updateUI() {
    setUI(DarculaDisclosureButtonUI())
  }
}
