// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.HelpTooltip
import com.intellij.ide.IdeBundle
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.InlineBannerBase
import com.intellij.ui.components.BrowserLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.ListLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.net.URL

internal object UnavailableWithoutSubscriptionComponent {
  @JvmStatic
  fun getHelpTooltip(): HelpTooltip? {
    val url = CommercialIdeData.get()?.purchaseUrl ?: return null

    return HelpTooltip()
      .setTitle(IdeBundle.message("plugin.unavailable.without.subscription.title"))
      .setDescription(IdeBundle.message("plugin.unavailable.without.subscription.description"))
      .setLocation(HelpTooltip.Alignment.CURSOR)
      .setBrowserLink(IdeBundle.message("link.learn.more"), url)
  }

  fun getBanner(): InlineBannerBase? {
    val ideData = CommercialIdeData.get() ?: return null
    return PluginBanner(ideData, IdeBundle.message("plugin.available.in.commercial.ide.text", ideData.name))
  }
}


internal object PartiallyAvailableComponent {
  fun getBanner(): InlineBannerBase? {
    val ideData = CommercialIdeData.get() ?: return null
    return PluginBanner(ideData, IdeBundle.message("plugin.has.ultimate.features.text", ideData.name))
  }
}


private class CommercialIdeData(val name: String, val purchaseLink: String) {
  val purchaseUrl: URL?
    get() = runCatching { URL(purchaseLink) }.getOrNull()

  companion object {
    val pyCharmProfessional = CommercialIdeData("PyCharm Pro",
                                                "https://www.jetbrains.com/pycharm/buy/?section=commercial&billing=yearly")

    fun get(): CommercialIdeData? = when {
      PlatformUtils.isPyCharmPro() -> pyCharmProfessional
      else -> null
    }
  }
}

private class PluginBanner(ideData: CommercialIdeData, val text: @Nls String) : InlineBannerBase(
  EditorNotificationPanel.Status.Info,
  JBUIScale.scale(8),
  text
) {
  init {
    layout = ListLayout.horizontal(JBUIScale.scale(10), ListLayout.Alignment.START)

    status.icon?.let { icon ->
      iconPanel.add(JBLabel(icon), BorderLayout.NORTH)
      add(iconPanel)
    }

    add(centerPanel)
    centerPanel.add(message)

    val link = BrowserLink(IdeBundle.message("link.purchase.subscription", ideData.name), ideData.purchaseLink)
    centerPanel.add(link)
  }
}
