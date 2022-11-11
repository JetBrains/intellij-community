// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.timeline

import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

object StatusMessageComponentFactory {
  fun create(messageComponent: JComponent, type: StatusMessageType = StatusMessageType.INFO): JComponent {
    val line = VerticalRoundedLineComponent(6).apply {
      foreground = when (type) {
        StatusMessageType.INFO -> JBColor.namedColor("Review.MetaInfo.StatusLine.Blue", ColorUtil.fromHex("#40B6E0B2"))
        StatusMessageType.SECONDARY_INFO -> JBColor.namedColor("Review.MetaInfo.StatusLine.Gray", ColorUtil.fromHex("#9AA7B0B3"))
        StatusMessageType.SUCCESS -> JBColor.namedColor("Review.MetaInfo.StatusLine.Green", ColorUtil.fromHex("#62B543B3"))
        StatusMessageType.WARNING -> JBColor.namedColor("Review.MetaInfo.StatusLine.Orange", ColorUtil.fromHex("#F26522B3"))
        StatusMessageType.ERROR -> JBColor.namedColor("Review.MetaInfo.StatusLine.Orange", ColorUtil.fromHex("#62B543B3"))
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