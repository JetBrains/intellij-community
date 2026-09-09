// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.IdeBundle
import com.intellij.ide.setToolTipText
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.components.Badge
import com.intellij.ui.components.labels.LinkListener
import org.jetbrains.annotations.Nls

internal object PluginTagBadge {
  @JvmStatic
  fun create(tag: @Nls String, searchListener: LinkListener<Any>): LinkComponent {
    return LinkComponent().apply {
      setPaintUnderline(false)
      isOpaque = false
      update(this, tag, searchListener)
    }
  }

  @JvmStatic
  fun update(component: LinkComponent, tag: @Nls String, searchListener: LinkListener<Any>) {
    component.text = ""
    component.icon = Badge(tag, colorType(tag))
    component.setToolTipText(tooltip(tag))
    component.accessibleContext.accessibleName = tag
    component.setListener(searchListener, SearchQueryParser.getTagQuery(tag))
  }

  internal fun colorType(tag: String): Badge.ColorType {
    return when (tag) {
      Tags.Paid.name, Tags.Ultimate.name, Tags.Pro.name -> Badge.ColorType.BLUE_SECONDARY
      Tags.Freemium.name, Tags.Purchased.name -> Badge.ColorType.GREEN_SECONDARY
      else -> Badge.ColorType.GRAY_SECONDARY
    }
  }

  private fun tooltip(tag: String): HtmlChunk? {
    val text = when (tag) {
      Tags.EAP.name -> IdeBundle.message("tooltip.eap.plugin.version")
      Tags.Paid.name, Tags.Freemium.name -> IdeBundle.message("tooltip.paid.plugin")
      Tags.Pro.name, Tags.Ultimate.name -> {
        val productCode = ApplicationInfoImpl.getShadowInstanceImpl().build.productCode
        val suggestedIdeCode = PluginAdvertiserService.getSuggestedCommercialIdeCode(productCode)
        val ide = suggestedIdeCode?.let { PluginAdvertiserService.getIde(it) }
        ide?.let { IdeBundle.message("tooltip.ultimate.plugin", it.name) }
      }
      else -> null
    }
    return text?.let { HtmlChunk.text(it) }
  }
}
