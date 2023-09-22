// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.chooser.ui

import com.intellij.importSettings.data.Configurable
import com.intellij.importSettings.data.Multiple
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JPanel

class MultipleSettingPane(val setting: Multiple, readOnly: Boolean) {
  val configurable = !readOnly && setting is Configurable

  private val list = convertList()
  private lateinit var actionLink: ActionLink

  val pane = panel {
    row {
      icon(setting.icon).align(AlignY.TOP).customize(UnscaledGaps(0, 0, 0, 8))
      panel {
        row {
          text(setting.name).customize(UnscaledGaps(0, 0, 2, 0)).resizableColumn()
          if (configurable) {
            checkBox("").customize(UnscaledGaps(0, 0, 2, 0))
          }
        }

        setting.comment?.let { addTxt ->
          row {
            comment(addTxt).customize(UnscaledGaps(0)).resizableColumn()
          }
        }

        val list = setting.list
        if (list.isNotEmpty()) {
          row {
            val text = if (configurable) {
              "Configure"
            }
            else {
              "Show all"
            }

            actionLink = ActionLink(object : AbstractAction(text) {
              override fun actionPerformed(e: ActionEvent) {
                showPopup()
              }
            }).apply {
              setDropDownLinkIcon()
            }

            cell(actionLink)
          }
        }
      }
    }
  }

  fun component(): JComponent {
    return pane
  }

  private fun showPopup() {
    val component = ChildSettingsList(list, configurable)

    val panel = JPanel(BorderLayout())
    panel.border = JBUI.Borders.empty()

    val scrollPane = JBScrollPane(component)
    panel.add(scrollPane, BorderLayout.CENTER)
    scrollPane.border = JBUI.Borders.empty(5)
    val chooserBuilder = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, component)
    chooserBuilder.createPopup().showUnderneathOf(actionLink)
  }

  private fun convertList(): List<SettingItem> {
    val list: MutableList<SettingItem> = mutableListOf()

    setting.list.forEach { cs ->
      if (cs.isNotEmpty()) {
        val elements = cs.map { SettingItem(it) }

        if (list.isNotEmpty()) {
          elements[0].separatorNeeded = true
        }
        list.addAll(elements)
      }
    }

    return list
  }
}
