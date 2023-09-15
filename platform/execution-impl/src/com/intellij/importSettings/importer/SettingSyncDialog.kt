// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.importer

import com.intellij.icons.AllIcons
import com.intellij.importSettings.data.ActionsDataProvider
import com.intellij.importSettings.data.BaseService
import com.intellij.importSettings.data.Product
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.*

class SettingSyncDialog(val service: ActionsDataProvider, val product: Product) : DialogWrapper(null) {

  private val pane = JPanel(BorderLayout()).apply {
    preferredSize = JBDimension(640, 410)
  }

  init {
    pane.add(JPanel(VerticalLayout(16)).apply {
      isOpaque = false

      add(JLabel("<html>Import<br>Settings From</html>").apply {
        font = JBFont.h1()
      })

      add(ItemPane(SettingSyncLabelInfo("IntelliJ IDEA Setting Sync",
                                        AllIcons.Actions.Refresh, "Synced yesterday")))
    }, BorderLayout.WEST)

    pane.add(JPanel().apply {
      border = JBUI.Borders.empty(16, 0)
      background = JBColor.BLACK
      preferredSize = JBDimension(420, 374)
    }, BorderLayout.EAST)

    init()
  }

  override fun createCenterPanel(): JComponent? {
    return pane
  }

  override fun getOKAction(): Action {
    return super.getOKAction().apply {
      putValue(Action.NAME, "Import Settings")
    }
  }

  override fun getCancelAction(): Action {
    return super.getCancelAction().apply {
      putValue(Action.NAME, "Back")
    }
  }
}

data class SettingSyncLabelInfo(val text: String, val icon: Icon?, val description: String?)

class ItemPane(info: SettingSyncLabelInfo) : JPanel(HorizontalLayout(8)) {

  var info = info
    set(value) {
      if(field == value) return

      field = value
      update()
    }

  private val icon = JLabel(info.icon).apply {
    font = JBFont.h1()
  }
  private val text = JLabel(info.text)
  private val description = JLabel(info.description).apply {
    foreground = UIUtil.getLabelInfoForeground()
  }

  init {
    isOpaque = false
    add(icon)
    add(JPanel(VerticalLayout(0)).apply {
      isOpaque = false
      add(text)
      add(description)
    })
  }

  private fun update() {
    icon.icon = info.icon
    icon.text = info.text
    icon.text = info.description
  }
}