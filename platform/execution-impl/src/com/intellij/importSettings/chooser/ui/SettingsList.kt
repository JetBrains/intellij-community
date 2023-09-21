// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.chooser.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ShowSettingsUtilImpl.Companion.showSettingsDialog
import com.intellij.importSettings.data.ChildSetting
import com.intellij.importSettings.data.Configurable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.CheckBoxList
import com.intellij.ui.CheckBoxListListener
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.ComponentOrientation
import java.awt.event.ActionEvent
import javax.swing.*

class SettingsList {

}

class ConfigurableSettingPane(val setting: Configurable, configurable: Boolean) {
  private val list = convertList()
  private lateinit var actionLink: ActionLink

  val pane = panel {
    row {
      icon(setting.icon).align(AlignY.TOP).customize(UnscaledGaps(0, 0, 0, 8))
      panel {
        row {
          text(setting.name).customize(UnscaledGaps(0, 0, 2, 0)).resizableColumn()
          checkBox("").customize(UnscaledGaps(0, 0, 2, 0))
        }

        setting.additionText?.let { addTxt ->
          row {
            comment(addTxt).customize(UnscaledGaps(0)).resizableColumn()
          }
        }

        val list = setting.list
        if (list.isNotEmpty()) {
          row {
            val text = if (configurable) {
              if (setting is Configurable) {
                "Configure"
              }
              else {
                "Show all"
              }
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
    val checkboxList = CheckboxList(list) { i, selected ->
      list[i].choosed = true
    }

    val panel = JPanel(BorderLayout())
    panel.border = JBUI.Borders.empty()

    val scrollPane = JBScrollPane(checkboxList)
    panel.add(scrollPane, BorderLayout.CENTER)
    scrollPane.border = JBUI.Borders.empty(5)
    val chooserBuilder = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, checkboxList)
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

private class SettingItem(val child: ChildSetting, var separatorNeeded: Boolean = false, var choosed: Boolean = true )

private class CheckboxList(val settings: List<SettingItem>, listener: CheckBoxListListener) : CheckBoxList<SettingItem>(listener) {
  init {
    //setItems(settings, {it.child.name})
    settings.forEach {
      addItem(it, it.child.name, it.choosed)
    }

  }

  override fun adjustRendering(rootComponent: JComponent,
                               checkBox: JCheckBox?,
                               index: Int,
                               selected: Boolean,
                               hasFocus: Boolean): JComponent {

    if(settings[index].separatorNeeded) {
      val itemWrapper = JPanel()
      itemWrapper.layout = BoxLayout(itemWrapper, BoxLayout.Y_AXIS)
      itemWrapper.add(SeparatorComponent(5, JBUI.CurrentTheme.Popup.separatorColor(), null))
      itemWrapper.add(rootComponent)
      return itemWrapper
    }

    return rootComponent
  }

}

