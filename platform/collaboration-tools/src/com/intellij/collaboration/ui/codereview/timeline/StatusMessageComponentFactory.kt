// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.timeline

import com.intellij.util.ui.JBUI
import java.awt.Color
import javax.swing.JComponent

object StatusMessageComponentFactory {
  fun create(messageComponent: JComponent, type: StatusMessageType = StatusMessageType.INFO): JComponent {
    val line = VerticalRoundedLineComponent(6).apply {
      foreground = when (type) {
        StatusMessageType.INFO -> Color(0xB3D8F8)
        StatusMessageType.SECONDARY_INFO -> Color(0xB5B9BD)
        StatusMessageType.SUCCESS -> Color(0xC7E3D4)
        StatusMessageType.ERROR -> Color(0xECC5C6)
        StatusMessageType.WARNING -> Color(0xF1E4C9)
      }
    }
    return JBUI.Panels.simplePanel(8, 0)
      .addToCenter(messageComponent.apply {
        border = JBUI.Borders.empty(2, 0)
      })
      .addToLeft(line)
      .andTransparent()
  }
}

enum class StatusMessageType {
  INFO,
  SECONDARY_INFO,
  SUCCESS,
  ERROR,
  WARNING;
}