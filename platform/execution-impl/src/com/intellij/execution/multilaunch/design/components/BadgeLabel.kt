package com.intellij.execution.multilaunch.design.components

import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBColor
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel

@ApiStatus.Internal
open class BadgeLabel(cornerRadius: Int = 4) : JPanel(BorderLayout()) {
  public var text: String
    get() = badgeContent.text
    set(@NlsSafe value) { badgeContent.text = value }

  public var icon: Icon?
    get() = badgeContent.icon
    set(@NlsSafe value) { badgeContent.icon = value }

  private val badgeContent = JLabel().apply {
    isOpaque = false
  }
  init {
    isOpaque = false
    background = JBColor.PanelBackground
    border = RoundedCornerBorder(cornerRadius)
    add(badgeContent, BorderLayout.CENTER)
  }
}

