// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel

class PrivacyNoticeComponent(@NlsContexts.Label private val label: String, @NlsContexts.Label private val expandedLabel: String) : JPanel(
  GridBagLayout()) {

  private val iconLabel: JLabel = JLabel()
  private val titleLabel = JLabel()
  private val privacyPolicyPane: JEditorPane = JEditorPane()

  var expanded: Boolean = true
    set(expanded) {
      field = expanded

      if (expanded) {
        titleLabel.text = expandedLabel
        iconLabel.icon = AllIcons.General.ArrowDown
        privacyPolicyPane.isVisible = true
      }
      else {
        titleLabel.text = label
        iconLabel.icon = AllIcons.General.ArrowRight
        privacyPolicyPane.isVisible = false
      }
    }

  var privacyPolicy: String
    get() = privacyPolicyPane.text
    set(@Nls(capitalization = Nls.Capitalization.Sentence) text) {
      privacyPolicyPane.text = text
    }

  init {
    background = backgroundColor()

    val iconLabelPanel = JPanel(BorderLayout())
    useInHeader(iconLabelPanel)
    iconLabelPanel.add(iconLabel, BorderLayout.WEST)

    val mySeparatorPanel = JPanel()
    useInHeader(mySeparatorPanel)
    mySeparatorPanel.preferredSize = Dimension(6, 1)

    useInHeader(titleLabel)
    titleLabel.foreground = titleColor()
    titleLabel.font = titleLabel.font.deriveFont((titleLabel.font.size - 1).toFloat())

    privacyPolicyPane.isEditable = false
    privacyPolicyPane.isFocusable = false
    privacyPolicyPane.background = backgroundColor()
    privacyPolicyPane.foreground = noticeColor()
    privacyPolicyPane.font = privacyPolicyPane.font.deriveFont((privacyPolicyPane.font.size - if (SystemInfo.isWindows) 2 else 1).toFloat())
    privacyPolicyPane.editorKit = HTMLEditorKitBuilder.simple()
    privacyPolicyPane.border = JBUI.Borders.empty(0, 0, 6, 6)
    privacyPolicyPane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)

    add(mySeparatorPanel, GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.VERTICAL,
                                             JBInsets.emptyInsets(),
                                             0, 0))
    add(iconLabelPanel,
        GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, JBInsets.emptyInsets(), 0, 0))
    add(titleLabel, GridBagConstraints(2, 0, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBInsets.emptyInsets(), 0,
                                       0))
    add(privacyPolicyPane, GridBagConstraints(2, 1, 1, 1, 0.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                              JBInsets.emptyInsets(), 0, 0))

    expanded = true
  }

  private fun useInHeader(component: JComponent) {
    component.border = JBUI.Borders.empty(6, 0)
    component.background = backgroundColor()
    component.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    component.addMouseListener(object : MouseAdapter() {
      override fun mouseReleased(e: MouseEvent?) {
        expanded = !expanded
      }
    })
  }

  companion object {
    private fun titleColor() = UIUtil.getLabelForeground()
    private fun noticeColor() = UIUtil.getContextHelpForeground()
    private fun backgroundColor() = ColorUtil.hackBrightness(JBUI.CurrentTheme.CustomFrameDecorations.paneBackground(), 1, 1 / 1.05f)
  }
}