// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.LinkPanel
import com.intellij.openapi.util.IntellijInternalApi
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
@IntellijInternalApi
class VendorInfoPanel : JPanel(ListLayout.horizontal(JBUI.scale(5))) {
  private val name = LinkPanel(this, false, false, null, null)
  private val verifiedIcon = ContextHelpLabel.create(IdeBundle.message("plugin.verified.organization"))
  private val traderStatus = JBLabel()
  private val traderIcon = ContextHelpLabel.create(IdeBundle.message("plugin.vendor.trader.status"))
  private val nonTraderIcon = ContextHelpLabel.create(IdeBundle.message("plugin.vendor.non.trader.status"))

  init {
    verifiedIcon.icon = AllIcons.Debugger.ThreadStates.Idle

    traderStatus.foreground = ListPluginComponent.GRAY_COLOR

    traderStatus.border = JBUI.Borders.emptyLeft(15)

    isOpaque = false

    add(verifiedIcon)
    add(traderStatus)
    add(traderIcon)
    add(nonTraderIcon)
  }

  fun show(node: PluginUiModel) {
    val vendorDetails = node.vendorDetails

    if (vendorDetails == null) {
      name.hide()
      isVisible = false
      traderIcon.isVisible = false
      nonTraderIcon.isVisible = false
      verifiedIcon.isVisible = false
      return
    }

    if (vendorDetails.url != null) {
      name.showWithBrowseUrl(
        IdeBundle.message("plugin.vendor.info.label", ""),
        vendorDetails.name,
        false
      ) { vendorDetails.url }
    } else {
      name.show(IdeBundle.message("plugin.vendor.info.label", vendorDetails.name), null)
    }

    verifiedIcon.isVisible = vendorDetails.isVerified()
    traderStatus.text = IdeBundle.message(if (vendorDetails.isTrader()) "plugin.vendor.trader.label" else "plugin.vendor.non.trader.label")
    traderIcon.isVisible = vendorDetails.isTrader()
    nonTraderIcon.isVisible = vendorDetails.isTrader().not()
    isVisible = true
  }
}