// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginNode
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.ListLayout
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.JPanel

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
class VendorInfoPanel : JPanel(ListLayout.horizontal(JBUI.scale(5))) {
  private val name = JBLabel()
  private val verifiedIcon = ContextHelpLabel.create(IdeBundle.message("plugin.verified.organization"))
  private val traderStatus = JBLabel()
  private val traderIcon = ContextHelpLabel.create(IdeBundle.message("plugin.vendor.trader.status"))
  private val nonTraderIcon = ContextHelpLabel.create(IdeBundle.message("plugin.vendor.non.trader.status"))

  init {
    verifiedIcon.icon = AllIcons.Debugger.ThreadStates.Idle

    name.foreground = ListPluginComponent.GRAY_COLOR
    traderStatus.foreground = ListPluginComponent.GRAY_COLOR

    traderStatus.border = JBUI.Borders.emptyLeft(15)

    isOpaque = false

    add(name)
    add(verifiedIcon)
    add(traderStatus)
    add(traderIcon)
    add(nonTraderIcon)
  }

  fun show(node: PluginNode) {
    name.text = IdeBundle.message("plugin.vendor.info.label", node.verifiedName)
    verifiedIcon.isVisible = node.isVerified
    traderStatus.text = IdeBundle.message(if (node.isTrader) "plugin.vendor.trader.label" else "plugin.vendor.non.trader.label")
    traderIcon.isVisible = node.isTrader
    nonTraderIcon.isVisible = !node.isTrader
    isVisible = node.verifiedName != null
  }
}