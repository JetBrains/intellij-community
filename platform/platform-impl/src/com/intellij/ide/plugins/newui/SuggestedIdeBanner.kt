// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.IdeBundle
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.SuggestedIde
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.ActionListener
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class SuggestedIdeBanner : JPanel() {
  private var suggestedIde: SuggestedIde? = null
  private var pluginId: PluginId? = null

  private val hintMessage: JLabel = JLabel("", SwingConstants.CENTER)
  private val downloadLink: ActionLink = ActionLink("", ActionListener {
    val downloadUrl = suggestedIde?.downloadUrl ?: return@ActionListener
    FUSEventSource.SEARCH.openDownloadPageAndLog(project = null, url = downloadUrl, pluginId = pluginId)
  })

  init {
    layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
    border = JBUI.Borders.empty(8)
    background = JBUI.CurrentTheme.Banner.INFO_BACKGROUND
    isVisible = false

    add(hintMessage)
    add(downloadLink, BorderLayout.SOUTH)

    hintMessage.alignmentX = CENTER_ALIGNMENT
    downloadLink.alignmentX = CENTER_ALIGNMENT

    hintMessage.foreground = JBUI.CurrentTheme.Banner.FOREGROUND
  }

  fun suggestIde(suggestedCommercialIde: String?, pluginId: PluginId?) {
    isVisible = suggestedCommercialIde != null

    this.pluginId = pluginId
    this.suggestedIde = PluginAdvertiserService.getIde(suggestedCommercialIde)

    if (suggestedIde != null) {
      val ideName = suggestedIde?.name
      hintMessage.text = IdeBundle.message("plugin.message.plugin.only.supported.in", ideName)
      downloadLink.text = IdeBundle.message("plugins.advertiser.action.try.ultimate", ideName)
    }
  }
}
