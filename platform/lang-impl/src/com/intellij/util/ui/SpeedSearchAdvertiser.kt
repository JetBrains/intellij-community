// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.components.ActionLink
import org.jetbrains.annotations.ApiStatus
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

@ApiStatus.Internal
class SpeedSearchAdvertiser : ComponentAdvertiser() {
  fun addSpeedSearchAdvertisement(): JComponent? {
    if (!Registry.`is`("popup.advertiser.speed.search")) {
      return null
    }

    if (PropertiesComponent.getInstance().getBoolean(SPEED_SEARCH_GOT_IT)) {
      return null
    }

    val panel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
      .apply { border = JBUI.Borders.empty() }

    val actionLink = ActionLink(IdeBundle.message("speed.search.got.it.link")) {
      PropertiesComponent.getInstance().setValue(SPEED_SEARCH_GOT_IT, true)
      panel.isVisible = false

      multiPanel.list.remove(panel)
      if (multiPanel.list.isEmpty()) {
        component.isVisible = false
        return@ActionLink
      }

      val i = currentIndex.get()
      multiPanel.select(currentIndex.updateAndGet { i % multiPanel.list.size }, true)

      nextLabel.isVisible = multiPanel.list.size > 1
    }.apply {
      font = adFont()
    }

    val label = JLabel(IdeBundle.message("speed.search.got.it.text"))
      .apply {
        foreground = JBUI.CurrentTheme.Advertiser.foreground()
        font = adFont()
      }

    panel.add(label)
    panel.add(actionLink)
    panel.background  = JBUI.CurrentTheme.Advertiser.background()

    addComponentAdvertiser(panel)

    return component
  }
}

private const val SPEED_SEARCH_GOT_IT = "SPEED_SEARCH_GOT_IT"